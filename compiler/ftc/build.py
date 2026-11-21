"""plugin folder -> <id>.ftplugin (zip). Validation + per-plugin assets. Carrier allocation happens later, in `pack`."""
from __future__ import annotations
import hashlib
import json
import zipfile
from pathlib import Path

from . import COMPILER_VERSION, SCHEMA
from .diag import Report
from .validate import (BUILDABLE_BLOCK_TEMPLATES, deps_of, validate_block, validate_item, validate_painting,
                       validate_plugin_json)

ITEM_BASE = {"generic": "minecraft:paper", "sword": "minecraft:iron_sword", "axe": "minecraft:iron_axe",
             "pickaxe": "minecraft:iron_pickaxe", "shovel": "minecraft:iron_shovel", "hoe": "minecraft:iron_hoe",
             "food": "minecraft:paper", "potion": "minecraft:paper", "custom": "minecraft:paper"}
ARMOR_BASE = {"head": "minecraft:iron_helmet", "chest": "minecraft:iron_chestplate", "legs": "minecraft:iron_leggings", "feet": "minecraft:iron_boots"}
HANDHELD = {"sword", "axe", "pickaxe", "shovel", "hoe"}
ZIP_EPOCH = (1980, 1, 1, 0, 0, 0)


def jdump(obj) -> bytes:
    return (json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")


def _children(folder: Path):
    return sorted(p for p in folder.iterdir() if p.is_dir() and not p.name.startswith(".")) if folder.is_dir() else []


def block_model(ns: str, bid: str, tex: dict) -> dict:
    ref = lambda f: f"{ns}:block/{bid}" if f == "all" else f"{ns}:block/{bid}_{f}"
    if set(tex) <= {"all", "particle"} or ("all" in tex and set(tex) == {"all"}):
        return {"parent": "minecraft:block/cube_all", "textures": {"all": ref("all")}}
    t = {}
    def pick(face, *chain):
        for c in chain:
            if c in tex: return ref(c)
        return ref("all") if "all" in tex else None
    t["down"] = pick("down", "bottom"); t["up"] = pick("up", "top")
    for d in ("north", "south", "east", "west"):
        t[d] = pick(d, d, "side")
    t["particle"] = pick("particle", "particle", "side", "top")
    return {"parent": "minecraft:block/cube", "textures": {k: v for k, v in t.items() if v}}


def build_plugin(src: Path, out_dir: Path, rep: Report, jar: Path | None = None) -> Path | None:
    src = src.resolve()
    pj = src / "plugin.json"
    if not pj.is_file():
        rep.error(str(src), 0, "E-MISSING", "plugin.json not found", "Run the compiler on the plugin folder that contains plugin.json.")
        return None
    pnode = validate_plugin_json(pj, src, rep)
    if pnode is None or not rep.ok:
        pass
    ns = pnode.value["id"].value if pnode and "id" in pnode.value and isinstance(pnode.value["id"].value, str) else None
    if ns is None:
        return None
    deps = deps_of(pnode)
    refs: list = []
    blocks, items, paintings, assets = [], [], [], {}
    seen: dict[str, str] = {}   # id -> where defined (items and blocks share one id space)

    def claim(kind, cid, rel, line):
        if cid in seen:
            rep.error(rel, line, "E-DUPLICATE", f"id \"{cid}\" is already used by {seen[cid]}", "Item and block ids share one namespace; rename one of them.")
            return False
        seen[cid] = rel
        return True

    # first pass: collect local ids so recipe references can be checked
    local_items, local_blocks, parsed = set(), set(), []
    for kind, folder_name, fn in (("block", "blocks", validate_block), ("item", "items", validate_item), ("painting", "paintings", validate_painting)):
        for folder in _children(src / folder_name):
            node = fn(folder, src, ns, deps, set(), refs, rep)
            if node is None: continue
            parsed.append((kind, folder, node))
            idn = node.value.get("id")
            if idn and isinstance(idn.value, str):
                if kind != "painting":
                    claim(kind, idn.value, f"{folder_name}/{folder.name}/config.json", idn.line)
                    (local_blocks if kind == "block" else local_items).add(idn.value)
    for (file, line, ref) in refs:
        rns, rid = ref.split(":", 1)
        if rns == ns and rid not in local_items | local_blocks:
            rep.error(file, line, "E-REFERENCE", f"\"{ref}\" is not defined in this plugin", f"Create items/{rid}/ or blocks/{rid}/, or fix the id.")
        elif rns not in (ns, "minecraft") and rns not in deps:
            rep.warn(file, line, "W-DEPENDENCY", f"\"{ref}\" uses namespace \"{rns}\" which is not listed in plugin.json dependencies", f"Add \"{rns}\" to \"dependencies\".")
    if not rep.ok:
        return None

    for kind, folder, node in parsed:
        cfg = node.py()
        cid = cfg["id"]
        rel_tex = lambda n: folder / n
        if kind == "block":
            if cfg["template"] not in BUILDABLE_BLOCK_TEMPLATES:
                continue   # warned in validation
            tex = cfg.get("textures", {})
            for face, fname in tex.items():
                name = f"{cid}" if face == "all" else f"{cid}_{face}"
                assets[f"assets/{ns}/textures/block/{name}.png"] = rel_tex(fname).read_bytes()
            assets[f"assets/{ns}/models/block/{cid}.json"] = jdump(block_model(ns, cid, tex))
            auto = cfg.get("item", {}).get("auto", True)
            if auto:
                assets[f"assets/{ns}/items/{cid}.json"] = jdump({"model": {"type": "minecraft:model", "model": f"{ns}:block/{cid}"}})
            entry = {k: v for k, v in cfg.items() if k not in ("schema", "textures", "model")}
            entry.update({"id": f"{ns}:{cid}", "model": f"{ns}:block/{cid}", "item": {"auto": auto, "stack_size": cfg.get("item", {}).get("stack_size", 64)}})
            blocks.append(entry)
            assets.setdefault(f"assets/{ns}/lang/en_us.json", {})[f"block.{ns}.{cid}"] = cfg["name"]
        elif kind == "item":
            tpl = cfg["template"]
            base = cfg.get("base") or (ARMOR_BASE[cfg["armor"]["slot"]] if tpl == "armor" and "armor" in cfg else ITEM_BASE.get(tpl, "minecraft:paper"))
            if "texture" in cfg:
                assets[f"assets/{ns}/textures/item/{cid}.png"] = rel_tex(cfg["texture"]).read_bytes()
                parent = "minecraft:item/handheld" if tpl in HANDHELD else "minecraft:item/generated"
                assets[f"assets/{ns}/models/item/{cid}.json"] = jdump({"parent": parent, "textures": {"layer0": f"{ns}:item/{cid}"}})
                assets[f"assets/{ns}/items/{cid}.json"] = jdump({"model": {"type": "minecraft:model", "model": f"{ns}:item/{cid}"}})
            entry = {k: v for k, v in cfg.items() if k not in ("schema", "texture", "model")}
            entry.update({"id": f"{ns}:{cid}", "base": base, "item_model": f"{ns}:{cid}"})
            entry.setdefault("stack_size", 64)
            items.append(entry)
            assets.setdefault(f"assets/{ns}/lang/en_us.json", {})[f"item.{ns}.{cid}"] = cfg["name"]
        else:
            assets[f"assets/{ns}/textures/painting/{cid}.png"] = rel_tex(cfg["texture"]).read_bytes()
            entry = {k: v for k, v in cfg.items() if k not in ("schema", "texture")}
            entry.update({"id": f"{ns}:{cid}", "asset_id": f"{ns}:{cid}"})
            paintings.append(entry)
            lang = assets.setdefault(f"assets/{ns}/lang/en_us.json", {})
            lang[f"painting.{ns}.{cid}.title"] = cfg["name"]
            if cfg.get("author"): lang[f"painting.{ns}.{cid}.author"] = cfg["author"]

    for k in list(assets):
        if k.endswith("/lang/en_us.json") and isinstance(assets[k], dict):
            assets[k] = jdump(assets[k])

    plugin = pnode.py()
    data_dir = src / "src" / "datapack"
    datapack = {}
    if data_dir.is_dir():
        for f in sorted(data_dir.rglob("*")):
            if f.is_file(): datapack["datapack/" + f.relative_to(data_dir).as_posix()] = f.read_bytes()
    java_dirs = [src / "src" / "main"]
    has_code = any(p.is_file() and p.suffix in (".java", ".kt") for d in java_dirs if d.is_dir() for p in d.rglob("*"))
    content = {"schema": SCHEMA, "plugin": {k: plugin[k] for k in ("id", "name", "version", "api") if k in plugin},
               "blocks": sorted(blocks, key=lambda b: b["id"]), "items": sorted(items, key=lambda b: b["id"]),
               "paintings": sorted(paintings, key=lambda b: b["id"]), "has_code": has_code, "has_datapack": bool(datapack)}
    files = {"content.json": jdump(content)}
    files.update({"pack/" + k: v for k, v in assets.items()})
    files.update(datapack)
    if jar:
        files["plugin.jar"] = Path(jar).read_bytes()
    elif has_code:
        rep.warn("src/main", 0, "W-NO-JAR", "src/main has code but no compiled jar was supplied; behaviours will not run", "Compile the code and pass --jar plugin.jar (code compilation arrives with roadmap item P3-09).")
    digests = {k: hashlib.sha256(v).hexdigest() for k, v in sorted(files.items())}
    manifest = {"schema": SCHEMA, "compiler": COMPILER_VERSION, "plugin": plugin, "files": digests}
    files["manifest.json"] = jdump(manifest)

    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{ns}.ftplugin"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(files):
            zi = zipfile.ZipInfo(name, ZIP_EPOCH)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[name])
    return out
