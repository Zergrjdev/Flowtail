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
from .carriers import all_states, key_of, load_properties, pool_slots
from .diag import Report

POOL = "cube.note_block"
POOL_BLOCK = "minecraft:note_block"


def _load_alloc(path: Path) -> dict:
    if path.is_file():
        d = json.loads(path.read_text(encoding="utf-8"))
        if d.get("schema") != SCHEMA:
            raise SystemExit(f"error: {path} has schema {d.get('schema')}, expected {SCHEMA}")
        return d
    return {"schema": SCHEMA, "pools": {POOL: {"block": POOL_BLOCK, "slots": {}}}}


def read_plugin(path: Path) -> dict:
    with zipfile.ZipFile(path) as z:
        files = {n: z.read(n) for n in z.namelist()}
    manifest = json.loads(files["manifest.json"])
    for n, digest in manifest["files"].items():
        if hashlib.sha256(files[n]).hexdigest() != digest:
            raise SystemExit(f"error: {path} is damaged: {n} does not match its recorded hash. Rebuild the plugin.")
    return {"manifest": manifest, "content": json.loads(files["content.json"]), "files": files, "path": path}


def build_pack(plugin_paths: list[Path], state_dir: Path, out_dir: Path, pack_format: int | list[int], rep: Report,
               blocks_report: Path | None = None, on_conflict: str = "first", description: str = "FlowTail content pack") -> dict:
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
    props, verified = load_properties(POOL_BLOCK, blocks_report)
    if not verified:
        rep.warn("carriers", 0, "W-CARRIER-UNVERIFIED", "carrier states come from the built-in fallback list, not the real server data",
                 "Run scripts/gen-reports.sh and pass --blocks-report build/reports/reports/blocks.json.")
    state_dir.mkdir(parents=True, exist_ok=True)
    apath = state_dir / "allocations.json"
    alloc = _load_alloc(apath)
    slots = alloc["pools"][POOL]["slots"]
    free = [s for s in pool_slots(props) if key_of(s) not in {key_of(v["state"]) for v in slots.values()}]

    # registry (merged)
    reg = {"schema": SCHEMA, "compiler": COMPILER_VERSION, "pack_format": pack_format, "plugins": [], "blocks": [], "items": [], "paintings": []}
    live_blocks = set()
    for p in plugins:
        c = p["content"]
        reg["plugins"].append({**c["plugin"], "has_code": c["has_code"], "has_datapack": c["has_datapack"]})
        reg["items"] += c["items"]; reg["paintings"] += c["paintings"]
        for b in c["blocks"]:
            live_blocks.add(b["id"])
            if b["id"] not in slots:
                if not free:
                    rep.error(f"{b['id']}", 0, "E-POOL-FULL", f"no free carrier states left in pool {POOL}", "Remove unused blocks or wait for more carrier pools (roadmap P3-02).")
                    continue
                slots[b["id"]] = {"state": free.pop(0), "retired": False}
            elif slots[b["id"]].get("retired"):
                slots[b["id"]]["retired"] = False
            reg["blocks"].append({**b, "carrier": {"pool": POOL, "block": POOL_BLOCK, "state": slots[b["id"]]["state"]}})
    for bid, v in slots.items():
        if bid not in live_blocks and not v.get("retired"):
            v["retired"] = True
            rep.warn(bid, 0, "W-BLOCK-RETIRED", f"block {bid} is no longer provided; its carrier slot stays reserved",
                     "Worlds that still contain it keep the block as an unknown block until the plugin returns.")
    if not rep.ok:
        return {}
    reg["retired_blocks"] = sorted(b for b, v in slots.items() if v.get("retired"))

    # merge pack files
    merged: dict[str, bytes] = {}
    owner: dict[str, str] = {}
    for p in plugins:
        pid = p["manifest"]["plugin"]["id"]
        for name, data in p["files"].items():
            if not name.startswith("pack/"):
                continue
            path = name[5:]
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

    # carrier blockstates: FULL variant list (vanilla states keep their vanilla model)
    variants = {}
    by_key = {key_of(v["state"]): bid for bid, v in slots.items()}
    for st in all_states(props):
        k = key_of(st)
        bid = by_key.get(k)
        model = None
        if bid and bid in live_blocks:
            model = next(b["model"] for b in reg["blocks"] if b["id"] == bid)
        elif bid:
            model = "minecraft:block/barrier"   # retired: visible placeholder, never another block's model
        else:
            model = "minecraft:block/note_block"
        variants[",".join(f"{n}={st[n]}" for n in sorted(st))] = {"model": model}
    merged["assets/minecraft/blockstates/note_block.json"] = jdump({"variants": variants})
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
