from pathlib import Path
from typing import Dict


class PromptLoader:
    def __init__(self, root: Path):
        self.root = root

    def load(self, name: str) -> str:
        path = self.root / f"{name}.txt"
        return path.read_text(encoding="utf-8")

    def render(self, name: str, values: Dict[str, str]) -> str:
        template = self.load(name)
        return template.format(**values)
