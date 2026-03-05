import json
from pathlib import Path
from typing import Dict, List

from .claude_client import ClaudeClient
from .models import ViolationBundle
from .prompts import PromptLoader


def run_variants(bundle: ViolationBundle, prompt_root: Path, output_path: Path, model: str = "claude-4") -> Dict[str, str]:
    client = ClaudeClient(model=model)
    loader = PromptLoader(prompt_root)
    variants = {
        "default": loader.render("generation", {
            "public_api_names": ", ".join(bundle.public_api_names),
            "target_statements": ", ".join(bundle.target_statements),
            "call_chain": " -> ".join(bundle.call_chain),
            "source_codes": "\n".join(bundle.source_codes),
            "rpc_server_code": bundle.rpc_server_code,
            "test_name": f"Test_{bundle.violation_id}",
        }),
        "full_codebase": "Generate test from full codebase context\n" + "\n".join(bundle.source_codes),
        "with_test_suite": loader.render("generation_with_test_suite", {
            "public_api_names": ", ".join(bundle.public_api_names),
            "target_statements": ", ".join(bundle.target_statements),
            "call_chain": " -> ".join(bundle.call_chain),
            "source_codes": "\n".join(bundle.source_codes),
            "rpc_server_code": bundle.rpc_server_code,
            "test_name": f"Test_{bundle.violation_id}",
            "test_suite_snippets": "\n".join(bundle.test_suite_snippets),
        }),
    }
    outputs = {}
    for name, prompt in variants.items():
        outputs[name] = client.generate(prompt, max_tokens=4096, temperature=0.2)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(outputs, indent=2), encoding="utf-8")
    return outputs
