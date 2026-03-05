import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, List, Tuple, TYPE_CHECKING

from .error_feedback import RuntimeErrorFeedbackParser
from .models import TestArtifact, ViolationBundle
from .prompts import PromptLoader
from .selector import PublicInterfaceSelector

if TYPE_CHECKING:
    from .claude_client import ClaudeClient


class RepairWorkflow:
    def __init__(self, client: "ClaudeClient", prompt_loader: PromptLoader):
        self.client = client
        self.prompts = prompt_loader
        self.parser = RuntimeErrorFeedbackParser()

    def repair(self, test_code: str, error_context: str, relevant_source_code: str, test_name: str) -> str:
        context = self.parser.parse(error_context)
        values = {
            "failing_test_code": test_code,
            "error_message": context.message,
            "relevant_source_code": relevant_source_code,
            "test_name": test_name,
        }
        prompt = self.prompts.render("repair", values)
        response = self._generate(prompt, use_history=True)
        return response or test_code

    def _generate(self, prompt: str, use_history: bool = False) -> str:
        try:
            return self.client.generate(prompt, use_history=use_history)
        except TypeError:
            return self.client.generate(prompt)


class ParameterSeedGenerator:
    def generate_sets(self, code: str, target_count: int = 16) -> List[Dict[str, str]]:
        params = self.extract_param_names(code)
        if not params:
            params = ["p0", "p1", "p2"]
        seeds = []
        for i in range(target_count):
            values = {}
            for idx, name in enumerate(params):
                values[name] = str((i + 1) * (idx + 2))
            seeds.append(values)
        return seeds

    def extract_param_names(self, code: str) -> List[str]:
        names = []
        for m in re.finditer(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=", code or ""):
            name = m.group(1)
            if name not in names and not name.startswith("this"):
                names.append(name)
        return names[:12]


class GenerationWorkflow:
    def __init__(self,
                 client: "ClaudeClient",
                 prompt_loader: PromptLoader,
                 repair_workflow: RepairWorkflow,
                 seed_generator: ParameterSeedGenerator,
                 repair_threshold: int = 8,
                 restart_cycles: int = 5,
                 compile_classpath: str = "",
                 runtime_classpath: str = "",
                 build_oracle: str = ""):
        self.client = client
        self.prompts = prompt_loader
        self.repair = repair_workflow
        self.seed_generator = seed_generator
        self.repair_threshold = repair_threshold
        self.restart_cycles = restart_cycles
        self.compile_classpath = compile_classpath or ""
        self.runtime_classpath = runtime_classpath or ""
        self.build_oracle = build_oracle or ""
        self.selector = PublicInterfaceSelector()

    def run(self, violation_bundle: ViolationBundle, adjacency: Dict[str, List[str]]) -> TestArtifact:
        ranked = self.selector.rank(
            target_function=violation_bundle.call_chain[-1] if violation_bundle.call_chain else "",
            interfaces=violation_bundle.public_api_names,
            adjacency=adjacency,
        )
        best = None
        for api in ranked:
            artifact = self._attempt_with_interface(violation_bundle, api, with_test_suites=False)
            if artifact.executable:
                return artifact
            best = artifact
        for api in ranked:
            artifact = self._attempt_with_interface(violation_bundle, api, with_test_suites=True)
            if artifact.executable:
                return artifact
            best = artifact
        return best if best is not None else TestArtifact(
            violation_id=violation_bundle.violation_id,
            test_name=f"Test{violation_bundle.violation_id}",
            code="public class EmptyTest { public static void main(String[] args) { } }",
            executable=False,
            parameters={},
            candidate_parameters=[],
        )

    def _attempt_with_interface(self, bundle: ViolationBundle, api: str, with_test_suites: bool) -> TestArtifact:
        name = f"Test_{bundle.violation_id.replace('-', '_')}"
        values = {
            "public_api_names": ", ".join([api]),
            "target_statements": ", ".join(bundle.target_statements),
            "call_chain": " -> ".join(bundle.call_chain),
            "source_codes": "\n".join(bundle.source_codes),
            "rpc_server_code": bundle.rpc_server_code,
            "test_name": name,
            "test_suite_snippets": "\n".join(bundle.test_suite_snippets),
        }
        prompt_name = "generation_with_test_suite" if with_test_suites else "generation"
        initial_prompt = self.prompts.render(prompt_name, values)
        if hasattr(self.client, "clear_history"):
            self.client.clear_history()
        for _ in range(self.restart_cycles):
            candidate = self._generate(initial_prompt, use_history=True)
            if not candidate:
                continue
            repaired, executable = self._repair_until_executable(candidate, bundle, name)
            seeds = self.seed_generator.generate_sets(repaired)
            params = seeds[0] if seeds else {}
            artifact = TestArtifact(
                violation_id=bundle.violation_id,
                test_name=self._class_name(repaired) or name,
                code=repaired,
                executable=executable,
                parameters=params,
                candidate_parameters=seeds,
            )
            if artifact.executable:
                return artifact
        return TestArtifact(
            violation_id=bundle.violation_id,
            test_name=name,
            code="",
            executable=False,
            parameters={},
            candidate_parameters=[],
        )

    def _repair_until_executable(self, candidate: str, bundle: ViolationBundle, name: str) -> Tuple[str, bool]:
        current = candidate
        for _ in range(self.repair_threshold):
            error = self._runtime_check(current, name)
            if not error:
                return current, True
            current = self.repair.repair(current, error, "\n".join(bundle.source_codes), name)
        return current, False

    def _runtime_check(self, code: str, fallback_name: str) -> str:
        if not code or not code.strip():
            return "empty test"
        class_name = self._class_name(code) or fallback_name
        javac = shutil.which("javac")
        java = shutil.which("java")
        if javac is None or java is None:
            return self._synthetic_runtime_check(code)
        try:
            with tempfile.TemporaryDirectory(prefix="themis_llm_") as tmp:
                root = Path(tmp)
                java_file = root / f"{class_name}.java"
                java_file.write_text(code, encoding="utf-8")
                compile_cmd = [javac]
                if self.compile_classpath:
                    compile_cmd.extend(["-cp", self.compile_classpath])
                compile_cmd.append(str(java_file))
                cp = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=45)
                if cp.returncode != 0:
                    return (cp.stderr or cp.stdout or "javac failed").strip()
                runtime_cp = str(root)
                if self.runtime_classpath:
                    runtime_cp = runtime_cp + os.pathsep + self.runtime_classpath
                rp = subprocess.run([java, "-cp", runtime_cp, class_name], capture_output=True, text=True, timeout=45)
                if rp.returncode != 0:
                    return (rp.stderr or rp.stdout or "java failed").strip()
                if self.build_oracle:
                    cwd = Path.cwd()
                    if not (cwd / "pom.xml").exists() and (cwd.parent / "pom.xml").exists():
                        cwd = cwd.parent
                    oracle = subprocess.run(
                        self.build_oracle,
                        shell=True,
                        cwd=str(cwd),
                        capture_output=True,
                        text=True,
                        timeout=90,
                    )
                    if oracle.returncode != 0:
                        return (oracle.stderr or oracle.stdout or "build oracle failed").strip()
                if "Thread" not in code and "Executor" not in code and "CompletableFuture" not in code:
                    return "No concurrency invocation"
                return ""
        except subprocess.TimeoutExpired:
            return "timeout"
        except Exception as exc:
            return str(exc)

    def _synthetic_runtime_check(self, code: str) -> str:
        if "class" not in code:
            return "cannot find symbol class"
        if "main(" not in code:
            return "cannot find symbol main"
        if "Thread" not in code:
            return "No concurrency invocation"
        return ""

    def _class_name(self, code: str) -> str:
        m = re.search(r"public\s+class\s+([A-Za-z_][A-Za-z0-9_]*)", code or "")
        if m:
            return m.group(1)
        m = re.search(r"class\s+([A-Za-z_][A-Za-z0-9_]*)", code or "")
        if m:
            return m.group(1)
        return ""

    def _generate(self, prompt: str, use_history: bool = False) -> str:
        try:
            return self.client.generate(prompt, use_history=use_history)
        except TypeError:
            return self.client.generate(prompt)


def write_artifacts(path: Path, artifacts: List[TestArtifact]) -> None:
    data = []
    for artifact in artifacts:
        data.append({
            "id": f"{artifact.violation_id}-{artifact.test_name}",
            "violationId": artifact.violation_id,
            "testClassName": artifact.test_name,
            "code": artifact.code,
            "publicInterfacePair": {
                "leftPublicMethod": "",
                "rightPublicMethod": "",
                "leftPath": [],
                "rightPath": [],
            },
            "primaryParameters": artifact.parameters,
            "candidateParameterSets": artifact.candidate_parameters,
            "executable": artifact.executable,
            "source": "llm",
        })
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
