#!/usr/bin/env python3
"""Create a compact source-only ZIP of the FlowTail project."""

from __future__ import annotations

import os
from pathlib import Path
import sys
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "FlowTail-source.zip"
EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".mypy_cache",
    ".pytest_cache",
    ".tools",
    ".venv",
    ".vscode",
    "__pycache__",
    "build",
    "cache",
    "dist",
    "libraries",
    "node_modules",
    "run",
    "target",
    "upstream",
}
EXCLUDED_SUFFIXES = {".class", ".jar", ".pyc", ".zip"}


def main() -> int:
    if sys.version_info < (3, 10):
        print("Python 3.10 or newer is required to create the source ZIP.", file=sys.stderr)
        return 1

    included = 0
    uncompressed_bytes = 0
    excluded_roots = sorted(EXCLUDED_DIRS & {p.name for p in ROOT.iterdir() if p.is_dir()})

    try:
        with ZipFile(OUTPUT, "w", compression=ZIP_DEFLATED, compresslevel=6) as archive:
            for current, dirs, files in os.walk(ROOT):
                current_path = Path(current)
                dirs[:] = sorted(
                    name for name in dirs
                    if name not in EXCLUDED_DIRS and not (current_path / name).is_symlink()
                )
                for filename in sorted(files):
                    path = current_path / filename
                    if path == OUTPUT or path.is_symlink() or path.suffix.lower() in EXCLUDED_SUFFIXES:
                        continue
                    relative = path.relative_to(ROOT)
                    archive_name = Path(ROOT.name) / relative
                    archive.write(path, archive_name.as_posix())
                    included += 1
                    uncompressed_bytes += path.stat().st_size
    except OSError as exc:
        print(f"Could not create {OUTPUT}: {exc}", file=sys.stderr)
        return 1

    archive_bytes = OUTPUT.stat().st_size
    print(f"Created: {OUTPUT}")
    print(f"Included {included} source and project files ({uncompressed_bytes / 1024 / 1024:.1f} MiB before compression).")
    print(f"ZIP size: {archive_bytes / 1024 / 1024:.1f} MiB")
    if excluded_roots:
        print("Skipped generated/dependency folders: " + ", ".join(excluded_roots))
    print("Also skipped nested build/cache folders, JARs, class files, Python bytecode, and existing ZIP files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
