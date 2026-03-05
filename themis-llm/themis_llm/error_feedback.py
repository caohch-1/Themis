import re
from dataclasses import dataclass
from typing import List


@dataclass
class ErrorContext:
    message: str
    symbols: List[str]
    classes: List[str]


class RuntimeErrorFeedbackParser:
    SYMBOL_PATTERN = re.compile(r"(?:cannot find symbol|NoSuchMethodError|NoSuchFieldError)[^\n]*")
    CLASS_PATTERN = re.compile(r"([A-Za-z_][A-Za-z0-9_$.]+)")

    def parse(self, message: str) -> ErrorContext:
        symbols = self.SYMBOL_PATTERN.findall(message or "")
        classes = []
        for match in self.CLASS_PATTERN.findall(message or ""):
            if "." in match and match[0].isalpha():
                classes.append(match)
        return ErrorContext(message=message or "", symbols=symbols, classes=classes)
