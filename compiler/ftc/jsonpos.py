"""JSON parser that remembers line numbers, so errors can say file:line."""
from __future__ import annotations


class JsonError(Exception):
    def __init__(self, msg: str, line: int):
        super().__init__(msg)
        self.msg, self.line = msg, line


class Node:
    """value for scalars; dict[str, Node] for objects; list[Node] for arrays."""
    __slots__ = ("value", "line", "key_lines")

    def __init__(self, value, line, key_lines=None):
        self.value, self.line, self.key_lines = value, line, key_lines or {}

    def py(self):
        if isinstance(self.value, dict):
            return {k: n.py() for k, n in self.value.items()}
        if isinstance(self.value, list):
            return [n.py() for n in self.value]
        return self.value

    @property
    def is_obj(self): return isinstance(self.value, dict)
    @property
    def is_arr(self): return isinstance(self.value, list)


class _P:
    def __init__(self, text: str):
        self.s, self.i, self.line = text, 0, 1

    def ws(self):
        while self.i < len(self.s) and self.s[self.i] in " \t\r\n":
            if self.s[self.i] == "\n":
                self.line += 1
            self.i += 1

    def err(self, msg):
        raise JsonError(msg, self.line)

    def value(self) -> Node:
        self.ws()
        if self.i >= len(self.s):
            self.err("unexpected end of file (a value was expected)")
        c = self.s[self.i]
        line = self.line
        if c == "{": return self.obj()
        if c == "[": return self.arr()
        if c == '"': return Node(self.string(), line)
        for lit, val in (("true", True), ("false", False), ("null", None)):
            if self.s.startswith(lit, self.i):
                self.i += len(lit)
                return Node(val, line)
        return self.number()

    def number(self) -> Node:
        line, st = self.line, self.i
        while self.i < len(self.s) and self.s[self.i] in "+-0123456789.eE":
            self.i += 1
        txt = self.s[st:self.i]
        if not txt:
            self.err(f"unexpected character {self.s[self.i]!r}")
        try:
            return Node(int(txt) if txt.lstrip("-").isdigit() else float(txt), line)
        except ValueError:
            self.err(f"bad number {txt!r}")

    def string(self) -> str:
        self.i += 1
        out = []
        while True:
            if self.i >= len(self.s):
                self.err("unterminated string")
            c = self.s[self.i]
            if c == '"':
                self.i += 1
                return "".join(out)
            if c == "\n":
                self.err("string runs past the end of the line (missing closing quote?)")
            if c == "\\":
                self.i += 1
                e = self.s[self.i] if self.i < len(self.s) else ""
                if e == "u":
                    out.append(chr(int(self.s[self.i + 1:self.i + 5], 16)))
                    self.i += 4
                else:
                    out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "/": "/", "\\": "\\", '"': '"'}.get(e, e))
            else:
                out.append(c)
            self.i += 1

    def obj(self) -> Node:
        line = self.line
        self.i += 1
        d, kl = {}, {}
        self.ws()
        if self.s[self.i:self.i + 1] == "}":
            self.i += 1
            return Node(d, line, kl)
        while True:
            self.ws()
            if self.s[self.i:self.i + 1] != '"':
                self.err("expected a quoted key (trailing comma?)")
            kline = self.line
            k = self.string()
            self.ws()
            if self.s[self.i:self.i + 1] != ":":
                self.err(f"expected ':' after key {k!r}")
            self.i += 1
            if k in d:
                self.err(f"duplicate key {k!r}")
            d[k] = self.value()
            kl[k] = kline
            self.ws()
            c = self.s[self.i:self.i + 1]
            self.i += 1
            if c == "}":
                return Node(d, line, kl)
            if c != ",":
                self.err("expected ',' or '}' (missing comma on the previous line?)")

    def arr(self) -> Node:
        line = self.line
        self.i += 1
        items = []
        self.ws()
        if self.s[self.i:self.i + 1] == "]":
            self.i += 1
            return Node(items, line)
        while True:
            items.append(self.value())
            self.ws()
            c = self.s[self.i:self.i + 1]
            self.i += 1
            if c == "]":
                return Node(items, line)
            if c != ",":
                self.err("expected ',' or ']' (missing comma on the previous line?)")


def parse(text: str) -> Node:
    p = _P(text.lstrip("\ufeff"))
    n = p.value()
    p.ws()
    if p.i != len(p.s):
        p.err("extra text after the end of the JSON value")
    return n
