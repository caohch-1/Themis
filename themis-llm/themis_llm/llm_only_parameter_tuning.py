import json
from pathlib import Path
from typing import Dict, List

from .claude_client import ClaudeClient
from .prompts import PromptLoader


def tune_loop(test_code: str,
              public_apis: List[str],
              target_statements: List[str],
              call_chain: List[str],
              source_codes: List[str],
              prompt_root: Path,
              output_path: Path,
              model: str = "claude-4",
              max_rounds: int = 10) -> Dict[str, str]:
    client = ClaudeClient(model=model)
    loader = PromptLoader(prompt_root)
    current = test_code
    history = {}
    for i in range(max_rounds):
        closest = infer_closest(current, target_statements)
        prompt = loader.render("llm_only_parameter_tuning", {
            "test_code": current,
            "target_statements": ", ".join(target_statements),
            "public_api_names": ", ".join(public_apis),
            "call_chain": " -> ".join(call_chain),
            "closest_statement": closest,
            "source_codes": "\n".join(source_codes),
            "test_name": f"Refined_{i}",
        })
        candidate = client.generate(prompt, max_tokens=4096, temperature=0.2)
        if not candidate:
            break
        current = candidate
        history[f"round_{i}"] = current
        if reached(current, target_statements):
            break
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(history, indent=2), encoding="utf-8")
    return history


def infer_closest(test_code: str, targets: List[str]) -> str:
    for target in targets:
        if target in test_code:
            return target
    return test_code.splitlines()[-1] if test_code.splitlines() else ""


def reached(code: str, targets: List[str]) -> bool:
    return all(t in code for t in targets)
