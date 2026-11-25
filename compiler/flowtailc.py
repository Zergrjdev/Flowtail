#!/usr/bin/env python3
"""FlowTail content compiler CLI (Python 3.10+, standard library only).

  flowtailc check <plugin-dir>
  flowtailc build <plugin-dir> [-o build/plugins] [--jar plugin.jar]
  flowtailc pack  <a.ftplugin> [b.ftplugin ...] [--state content-state] [-o build/pack] [--pack-format N]
"""
from __future__ import annotations
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ftc.build import build_plugin          # noqa: E402
from ftc.diag import Report                 # noqa: E402
from ftc.pack import build_pack             # noqa: E402


def parse_pack_format(value: str | int | list[int]) -> list[int]:
    """Normalize the modern resource-pack version to [major, minor]."""
    if isinstance(value, int):
        return [value, 0]
    if isinstance(value, list) and len(value) in (1, 2) and all(isinstance(x, int) for x in value):
        return [value[0], value[1] if len(value) == 2 else 0]
    if isinstance(value, str):
        parts = value.split(".", 1)
        if all(part.isdigit() for part in parts):
            return [int(parts[0]), int(parts[1]) if len(parts) == 2 else 0]
    raise argparse.ArgumentTypeError("pack format must be MAJOR or MAJOR.MINOR (for example 97.1)")


def default_pack_format() -> list[int]:
    try:
        import tomllib
        t = tomllib.loads((Path(__file__).resolve().parents[1] / "versions.toml").read_text())
        return parse_pack_format(t["minecraft"]["resource_pack_format"])
    except Exception:
        return [97, 1]


def show(rep: Report) -> None:
    for d in rep.items:
        print(d, file=sys.stderr)
    print(f"{len(rep.errors)} error(s), {len(rep.warnings)} warning(s)", file=sys.stderr)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="flowtailc", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check"); c.add_argument("plugin_dir", type=Path)
    b = sub.add_parser("build"); b.add_argument("plugin_dir", type=Path); b.add_argument("-o", "--out", type=Path, default=Path("build/plugins")); b.add_argument("--jar", type=Path)
    p = sub.add_parser("pack"); p.add_argument("plugins", nargs="+", type=Path)
    p.add_argument("--state", type=Path, default=Path("content-state")); p.add_argument("-o", "--out", type=Path, default=Path("build/pack"))
    p.add_argument("--pack-format", type=parse_pack_format, default=default_pack_format())
    p.add_argument("--on-conflict", choices=["first", "last", "error"], default="first")
    a = ap.parse_args(argv)
    rep = Report()
    if a.cmd in ("check", "build"):
        tmp = Path(a.out) if a.cmd == "build" else Path("/nonexistent-check-only")
        if a.cmd == "check":
            import tempfile
            with tempfile.TemporaryDirectory() as td:
                build_plugin(a.plugin_dir, Path(td), rep)
        else:
            out = build_plugin(a.plugin_dir, tmp, rep, a.jar)
            if out: print(f"built {out}")
        show(rep)
        if a.cmd == "check" and rep.ok: print("ok")
        return 0 if rep.ok else 1
    info = build_pack(a.plugins, a.state, a.out, a.pack_format, rep, a.on_conflict)
    show(rep)
    if info:
        print(f"pack {a.out}/pack.zip sha1={info['sha1']} size={info['size']} entries={info['entries']} pack_format={info['pack_format']}")
    return 0 if rep.ok and info else 1


if __name__ == "__main__":
    sys.exit(main())
