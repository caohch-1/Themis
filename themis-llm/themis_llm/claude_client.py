import os
from typing import Dict, List, Optional

from anthropic import Anthropic


class ClaudeClient:
    def __init__(self, model: str = "claude-4", api_key: Optional[str] = None, history_window: int = 24):
        self.model = model
        key = api_key or os.getenv("ANTHROPIC_API_KEY", "")
        self.client = Anthropic(api_key=key) if key else None
        self.history_window = history_window
        self.history: List[Dict[str, str]] = []

    def clear_history(self) -> None:
        self.history = []

    def generate(self, prompt: str, max_tokens: int = 4096, temperature: float = 0.2, use_history: bool = False) -> str:
        if self.client is None:
            return ""
        messages: List[Dict[str, str]] = []
        if use_history and self.history:
            messages.extend(self.history[-self.history_window:])
        messages.append({"role": "user", "content": prompt})
        msg = self.client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            temperature=temperature,
            messages=messages,
        )
        if not msg.content:
            return ""
        text_parts = []
        for item in msg.content:
            if hasattr(item, "text"):
                text_parts.append(item.text)
        response = "\n".join(text_parts)
        if use_history:
            self.history.append({"role": "user", "content": prompt})
            self.history.append({"role": "assistant", "content": response})
            if len(self.history) > self.history_window * 2:
                self.history = self.history[-self.history_window * 2 :]
        return response
