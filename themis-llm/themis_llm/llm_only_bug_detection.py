import json
from pathlib import Path
from typing import List

from .claude_client import ClaudeClient
from .prompts import PromptLoader


def run(codebase_snapshot: str, prompt_root: Path, output_path: Path, model: str = "claude-4") -> List[str]:
    client = ClaudeClient(model=model)
    loader = PromptLoader(prompt_root)
    prompt = loader.render("llm_only_bug_detection", {}) + "\n" + codebase_snapshot
    response = client.generate(prompt, max_tokens=4096, temperature=0.3)
    lines = [line.strip() for line in response.splitlines() if line.strip()]
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(lines, indent=2), encoding="utf-8")
    return lines
