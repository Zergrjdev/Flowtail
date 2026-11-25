"""N x .ftplugin -> one resource pack + registry.json + allocations.json (persistent carrier slots).

Rules (docs/13):
 * Carrier slots are APPEND-ONLY. A block keeps its slot forever; removed blocks are marked retired and their slot is never reused,
   so an old world can never show a different block in an old position.
 * Output is deterministic: same inputs -> same entries; zip entries sorted, fixed timestamps.
 * Conflicts (same path from two plugins) are reported; the first plugin in load order wins unless --on-conflict says otherwise.
"""
from __future__ import annotations
import hashlib
import json
import zipfile
from pathlib import Path

from . import COMPILER_VERSION, SCHEMA
from .build import ZIP_EPOCH, jdump
from .diag import Report



def _load_alloc(path: Path) -> dict:
    if path.is_file():
        d = json.loads(path.read_text(encoding="utf-8"))
        if d.get("schema") != SCHEMA:
            raise SystemExit(f"error: {path} has schema {d.get('schema')}, expected {SCHEMA}")
        return d
    return {"schema": SCHEMA, "blocks": {}}


def read_plugin(path: Path) -> dict:
    with zipfile.ZipFile(path) as z:
        files = {n: z.read(n) for n in z.namelist()}
    manifest = json.loads(files["manifest.json"])
    for n, digest in manifest["files"].items():
        if hashlib.sha256(files[n]).hexdigest() != digest:
            raise SystemExit(f"error: {path} is damaged: {n} does not match its recorded hash. Rebuild the plugin.")
    return {"manifest": manifest, "content": json.loads(files["content.json"]), "files": files, "path": path}


def build_pack(plugin_paths: list[Path], state_dir: Path, out_dir: Path, pack_format: int | list[int], rep: Report,
               on_conflict: str = "first", description: str = "FlowTail content pack") -> dict:
    # Keep direct Python callers that still pass a major-only format working;
    # the CLI/default from versions.toml always supplies [major, minor].
    if isinstance(pack_format, int):
        pack_format = [pack_format, 0]
    if len(pack_format) not in (1, 2) or any(not isinstance(part, int) for part in pack_format):
        raise ValueError("pack_format must be a major integer or [major, minor]")
    pack_format = [pack_format[0], pack_format[1] if len(pack_format) == 2 else 0]
    plugins = sorted((read_plugin(p) for p in plugin_paths), key=lambda p: p["manifest"]["plugin"]["id"])
    ids = [p["manifest"]["plugin"]["id"] for p in plugins]
    for i in {x for x in ids if ids.count(x) > 1}:
        rep.error("plugins", 0, "E-DUPLICATE-PLUGIN", f"plugin id \"{i}\" appears more than once", "Remove the older .ftplugin file.")
    if not rep.ok:
        return {}
    state_dir.mkdir(parents=True, exist_ok=True)
    apath = state_dir / "allocations.json"
    alloc = _load_alloc(apath)
    known_blocks = alloc.setdefault("blocks", {})
    # Keep legacy allocations readable for existing installs, but never assign or emit carrier states.
    slots = alloc.get("pools", {}).get("cube.note_block", {}).get("slots", {})
    for legacy_id, legacy_slot in slots.items():
        known_blocks.setdefault(legacy_id, not legacy_slot.get("retired", False))
    alloc.pop("pools", None)

    # registry (merged)
    reg = {"schema": SCHEMA, "compiler": COMPILER_VERSION, "pack_format": pack_format, "plugins": [], "blocks": [], "items": [], "paintings": []}
    live_blocks = set()
    for p in plugins:
        c = p["content"]
        reg["plugins"].append({**c["plugin"], "has_code": c["has_code"], "has_datapack": c["has_datapack"]})
        reg["items"] += c["items"]; reg["paintings"] += c["paintings"]
        for b in c["blocks"]:
            live_blocks.add(b["id"])
            known_blocks[b["id"]] = True
            if b["id"] in slots and slots[b["id"]].get("retired"):
                slots[b["id"]]["retired"] = False
            reg["blocks"].append(dict(b))
    for bid in list(known_blocks):
        if bid not in live_blocks:
            known_blocks[bid] = False
    retired_ids = sorted(bid for bid, live in known_blocks.items() if not live)
    if not rep.ok:
        return {}
    reg["retired_blocks"] = retired_ids

    # merge pack files
    merged: dict[str, bytes] = {}
    owner: dict[str, str] = {}
    for p in plugins:
        pid = p["manifest"]["plugin"]["id"]
        for name, data in p["files"].items():
            if not name.startswith("pack/"):
                continue
            path = name[5:]
            if (path.startswith("assets/minecraft/blockstates/")
                    or path.startswith("assets/minecraft/models/block/")
                    or path.startswith("assets/minecraft/textures/block/")):
                rep.error(path, 0, "E-VANILLA-BLOCK-OVERRIDE",
                          "plugins may not replace vanilla blockstates, block models, or block textures",
                          "Put custom block assets in the plugin namespace and use the FlowTail item_model projection.")
                continue
            if path in merged:
                if merged[path] == data:
                    continue
                msg = f"{path} is provided by both \"{owner[path]}\" and \"{pid}\" with different content"
                if on_conflict == "error":
                    rep.error(path, 0, "E-PACK-CONFLICT", msg, "Rename the asset in one plugin.")
                    continue
                rep.warn(path, 0, "W-PACK-CONFLICT", msg + f"; keeping {'the first' if on_conflict == 'first' else 'the last'}", "Rename the asset in one plugin.")
                if on_conflict == "first":
                    continue
            merged[path] = data
            owner[path] = pid
    # language files in the same namespace are separate paths, so no merge is needed

    # Add custom models and item definitions only. Never replace a vanilla blockstate or model.
    merged["pack.mcmeta"] = jdump({"pack": {"description": description, "min_format": pack_format, "max_format": pack_format}})
    if not rep.ok:
        return {}

    out_dir.mkdir(parents=True, exist_ok=True)
    zpath = out_dir / "pack.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(merged):
            zi = zipfile.ZipInfo(name, ZIP_EPOCH)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, merged[name])
    raw = zpath.read_bytes()
    info = {"sha1": hashlib.sha1(raw).hexdigest(), "size": len(raw), "pack_format": pack_format, "entries": len(merged)}
    (out_dir / "pack.sha1").write_text(info["sha1"] + "\n")
    (out_dir / "pack.json").write_bytes(jdump(info))
    (out_dir / "registry.json").write_bytes(jdump(reg))
    apath.write_bytes(jdump(alloc))
    return info
