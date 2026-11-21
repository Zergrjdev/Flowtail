from __future__ import annotations
from dataclasses import dataclass


@dataclass
class Diag:
    file: str
    line: int
    severity: str   # "error" | "warning"
    code: str
    message: str
    fix: str = ""

    def __str__(self):
        loc = f"{self.file}:{self.line}" if self.line else self.file
        s = f"{loc}: {self.severity}[{self.code}]: {self.message}"
        return s + (f"\n    fix: {self.fix}" if self.fix else "")


DEFAULT_FIX = {
    "E-RANGE": "Change the number to a value inside the range named in the message.",
    "E-TYPE": "Change the value to the type named in the message.",
    "E-RECIPE": "Compare with the recipe example in examples/example-plugin.",
    "E-STATE": "Compare with the states example in docs/04_CUSTOM_CONTENT.md.",
    "E-HITBOX": "Full block: [0,0,0,16,16,16].",
}


class Report:
    def __init__(self):
        self.items: list[Diag] = []

    def error(self, file, line, code, msg, fix=""):
        self.items.append(Diag(str(file), line or 0, "error", code, msg, fix or DEFAULT_FIX.get(code, "")))

    def warn(self, file, line, code, msg, fix=""):
        self.items.append(Diag(str(file), line or 0, "warning", code, msg, fix))

    @property
    def errors(self): return [d for d in self.items if d.severity == "error"]
    @property
    def warnings(self): return [d for d in self.items if d.severity == "warning"]
    @property
    def ok(self): return not self.errors


def levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(cur[-1] + 1, prev[j] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def suggest(word: str, options) -> str | None:
    best = min(options, key=lambda o: levenshtein(word, o), default=None)
    return best if best is not None and levenshtein(word, best) <= max(2, len(word) // 3) else None
