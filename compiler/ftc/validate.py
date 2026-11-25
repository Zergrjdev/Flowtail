"""Schema v0 validation for plugin.json, blocks/, items/, paintings/. Every problem is file:line + what + how to fix."""
from __future__ import annotations
import re
import struct
from pathlib import Path

from . import SCHEMA
from .diag import Report, suggest
from .jsonpos import JsonError, Node, parse

ID_RE = re.compile(r"^[a-z0-9_]+$")
PLUGIN_ID_RE = re.compile(r"^[a-z][a-z0-9_]{1,31}$")
RES_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$")

BLOCK_TEMPLATES = ["cube", "slab", "stairs", "trapdoor", "button", "pressure_plate", "carpet", "shelf", "plant", "custom_model"]
BUILDABLE_BLOCK_TEMPLATES = ["cube", "slab", "stairs", "trapdoor", "button", "pressure_plate", "carpet", "plant", "shelf"]   # P3-01
TEMPLATE_LATER = {   # valid names that are not built yet, and the honest reason
    "custom_model": "complex Blockbench/Java models arrive with roadmap item P3-12",
}
ITEM_TEMPLATES = ["generic", "sword", "axe", "pickaxe", "shovel", "hoe", "armor", "food", "potion", "custom"]
FACES = ["all", "top", "bottom", "side", "north", "south", "east", "west", "particle"]
TOOLS = ["none", "pickaxe", "axe", "shovel", "hoe", "sword", "shears"]
TIERS = ["none", "wood", "stone", "iron", "diamond", "netherite", "gold"]
SOUND_PRESETS = ["stone", "wood", "grass", "gravel", "sand", "metal", "glass", "wool", "snow", "bone"]
BEHAVIOR_EVENTS = ["on_place", "on_break", "on_interact", "on_tick", "on_neighbor_change", "on_use", "on_hit", "on_equip", "on_unequip", "on_consume", "on_drop"]
ARMOR_SLOTS = ["head", "chest", "legs", "feet"]
RECIPE_TYPES = ["shaped", "shapeless", "smelting", "blasting", "smoking", "campfire_cooking", "stonecutting"]

BLOCK_KEYS = ["schema", "id", "name", "template", "textures", "model", "hitbox", "states", "mining", "drops", "sounds", "particles", "light", "ticking", "explosion", "item", "recipes", "behaviors"]
ITEM_KEYS = ["schema", "id", "name", "template", "texture", "model", "stack_size", "stats", "armor", "food", "components", "recipes", "behaviors", "base"]
PAINTING_KEYS = ["schema", "id", "name", "author", "texture", "width", "height", "item", "recipes"]
PLUGIN_KEYS = ["schema", "id", "name", "version", "authors", "api", "main", "dependencies", "description"]


def png_size(path: Path):
    d = path.read_bytes()[:24]
    if len(d) < 24 or d[:8] != b"\x89PNG\r\n\x1a\n" or d[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", d[16:24])


class Ctx:
    def __init__(self, report: Report, file: Path, root: Path):
        self.r, self.file, self.root = report, file, root
        self.rel = str(file.relative_to(root)) if root in file.parents else str(file)

    def err(self, node_or_line, code, msg, fix=""):
        self.r.error(self.rel, getattr(node_or_line, "line", node_or_line), code, msg, fix)

    def warn(self, node_or_line, code, msg, fix=""):
        self.r.warn(self.rel, getattr(node_or_line, "line", node_or_line), code, msg, fix)

    def key_line(self, obj: Node, key: str):
        return obj.key_lines.get(key, obj.line)


def _load(ctx: Ctx):
    try:
        return parse(ctx.file.read_text(encoding="utf-8"))
    except JsonError as e:
        ctx.err(e.line, "E-JSON", f"invalid JSON: {e.msg}", "Fix the syntax. A JSON checker such as jsonlint.com shows the same problem.")
    except FileNotFoundError:
        ctx.err(0, "E-MISSING", "file not found", "Create the file.")
    except UnicodeDecodeError:
        ctx.err(0, "E-ENCODING", "file is not UTF-8 text", "Save the file as UTF-8.")
    return None


def _need(ctx, obj: Node, key: str, kind):
    """Required field of a python type (or tuple). Returns the Node or None after reporting."""
    n = obj.value.get(key)
    if n is None:
        ctx.err(obj, "E-REQUIRED", f"missing required field \"{key}\"", f"Add \"{key}\" to this object.")
        return None
    if not _is(n.value, kind):
        ctx.err(n, "E-TYPE", f"\"{key}\" must be {_kname(kind)}", f"Change \"{key}\" to {_kname(kind)}.")
        return None
    return n


def _opt(ctx, obj: Node, key: str, kind):
    n = obj.value.get(key)
    if n is None:
        return None
    if not _is(n.value, kind):
        ctx.err(n, "E-TYPE", f"\"{key}\" must be {_kname(kind)}", f"Change \"{key}\" to {_kname(kind)}.")
        return None
    return n


def _is(v, kind):
    if kind is bool: return isinstance(v, bool)
    if kind is int: return isinstance(v, int) and not isinstance(v, bool)
    if kind is float: return isinstance(v, (int, float)) and not isinstance(v, bool)
    if kind is str: return isinstance(v, str)
    if kind is dict: return isinstance(v, dict)
    if kind is list: return isinstance(v, list)
    return False


def _kname(kind):
    return {bool: "true or false", int: "a whole number", float: "a number", str: "text in quotes", dict: "an object { }", list: "a list [ ]"}[kind]


def _unknown_keys(ctx, obj: Node, allowed):
    for k in obj.value:
        if k not in allowed:
            s = suggest(k, allowed)
            ctx.warn(ctx.key_line(obj, k), "W-UNKNOWN-KEY", f"unknown field \"{k}\" is ignored", f"Did you mean \"{s}\"?" if s else "Remove it.")


def _schema(ctx, obj):
    n = _need(ctx, obj, "schema", int)
    if n and n.value != SCHEMA:
        ctx.err(n, "E-SCHEMA", f"schema {n.value} is not supported (this compiler reads schema {SCHEMA})", f"Set \"schema\": {SCHEMA}.")


def _enum(ctx, node: Node, options, what):
    if node.value not in options:
        s = suggest(str(node.value), options)
        ctx.err(node, "E-ENUM", f"{what} \"{node.value}\" is not valid. Options: {', '.join(options)}", f"Did you mean \"{s}\"?" if s else f"Use one of: {', '.join(options)}.")
        return False
    return True


def _png(ctx, folder: Path, node: Node, what: str):
    """Check a texture path relative to the content folder. Returns (path, (w,h)) or None."""
    rel = node.value
    if not isinstance(rel, str):
        ctx.err(node, "E-TYPE", f"{what} must be a file path in quotes"); return None
    if rel.startswith("/") or ".." in Path(rel).parts:
        ctx.err(node, "E-PATH", f"{what} \"{rel}\" must stay inside this folder", "Use a plain file name such as \"texture.png\"."); return None
    if not rel.endswith(".png"):
        ctx.err(node, "E-TEXTURE", f"{what} \"{rel}\" must be a .png file", "Export the texture as PNG."); return None
    p = folder / rel
    if not p.is_file():
        ctx.err(node, "E-TEXTURE", f"{what} file \"{rel}\" not found in {folder.parent.name}/{folder.name}/", f"Put the file at {folder.parent.name}/{folder.name}/{rel} or fix the name."); return None
    size = png_size(p)
    if size is None:
        ctx.err(node, "E-TEXTURE", f"\"{rel}\" is not a valid PNG image", "Re-export the image as a real PNG."); return None
    return p, size


def _recipes(ctx, obj: Node, own_ns: str, deps: set, known_local: set, refs: list):
    n = _opt(ctx, obj, "recipes", list)
    if n is None:
        return
    for rn in n.value:
        if not rn.is_obj:
            ctx.err(rn, "E-TYPE", "a recipe must be an object { }"); continue
        t = _need(ctx, rn, "type", str)
        if not t: continue
        if not _enum(ctx, t, RECIPE_TYPES, "recipe type"): continue
        if t.value not in ("shaped", "shapeless"):
            ctx.warn(rn, "W-RECIPE-LATER", f"recipe type \"{t.value}\" is stored but not built until roadmap item P3-08"); continue
        if t.value == "shaped":
            pat, key = _need(ctx, rn, "pattern", list), _need(ctx, rn, "key", dict)
            if pat and key:
                rows = [x.value for x in pat.value]
                if not rows or len(rows) > 3 or any(not isinstance(x, str) for x in rows):
                    ctx.err(pat, "E-RECIPE", "pattern must be 1 to 3 rows of text", "Example: [\" R \", \" R \", \" S \"]")
                elif len({len(x) for x in rows}) != 1 or not (1 <= len(rows[0]) <= 3):
                    ctx.err(pat, "E-RECIPE", "every pattern row must have the same length, 1 to 3 characters", "Pad short rows with spaces.")
                else:
                    used = {c for row in rows for c in row if c != " "}
                    for c in sorted(used - set(key.value)):
                        ctx.err(pat, "E-RECIPE", f"pattern uses '{c}' but \"key\" has no entry for it", f"Add \"{c}\": \"namespace:item\" to \"key\".")
                    for c in sorted(set(key.value) - used):
                        ctx.warn(key.value[c], "W-RECIPE", f"key '{c}' is never used in the pattern")
                for c, v in key.value.items():
                    _ref(ctx, v, own_ns, deps, known_local, refs)
            rc = _opt(ctx, rn, "result_count", int)
            if rc and not (1 <= rc.value <= 64):
                ctx.err(rc, "E-RANGE", "result_count must be 1 to 64")
        else:
            ing = _need(ctx, rn, "ingredients", list)
            if ing:
                if not (1 <= len(ing.value) <= 9):
                    ctx.err(ing, "E-RECIPE", "ingredients must list 1 to 9 items")
                for v in ing.value:
                    _ref(ctx, v, own_ns, deps, known_local, refs)


def _ref(ctx, node: Node, own_ns: str, deps: set, known_local: set, refs: list):
    v = node.value
    if not isinstance(v, str) or not RES_RE.match(v):
        ctx.err(node, "E-RESID", f"\"{v}\" is not an item id", "Use namespace:name, for example minecraft:stick or " + own_ns + ":ruby."); return
    refs.append((ctx.rel, node.line, v))


def _behaviors(ctx, obj: Node):
    n = _opt(ctx, obj, "behaviors", dict)
    if n is None: return
    for ev, fn in n.value.items():
        if ev not in BEHAVIOR_EVENTS:
            s = suggest(ev, BEHAVIOR_EVENTS)
            ctx.err(n.key_lines.get(ev, n.line), "E-EVENT", f"unknown event \"{ev}\"", f"Did you mean \"{s}\"?" if s else f"Events: {', '.join(BEHAVIOR_EVENTS)}.")
        if not isinstance(fn.value, str) or not RES_RE.match(fn.value):
            ctx.err(fn, "E-RESID", "a behaviour must be a function id like myplugin:fire_burst")


def validate_plugin_json(path: Path, root: Path, rep: Report):
    ctx = Ctx(rep, path, root)
    node = _load(ctx)
    if node is None: return None
    if not node.is_obj:
        ctx.err(node, "E-TYPE", "plugin.json must be an object { }"); return None
    _schema(ctx, node); _unknown_keys(ctx, node, PLUGIN_KEYS)
    i = _need(ctx, node, "id", str)
    if i and not PLUGIN_ID_RE.match(i.value):
        ctx.err(i, "E-ID", f"plugin id \"{i.value}\" is not valid", "Use 2-32 lowercase letters, digits or _, starting with a letter.")
    _need(ctx, node, "name", str)
    v = _need(ctx, node, "version", str)
    if v and not SEMVER_RE.match(v.value):
        ctx.err(v, "E-VERSION", f"version \"{v.value}\" is not major.minor.patch", "Example: 0.1.0")
    a = _need(ctx, node, "authors", list)
    if a and (not a.value or any(not isinstance(x.value, str) for x in a.value)):
        ctx.err(a, "E-TYPE", "authors must be a list of names in quotes")
    _need(ctx, node, "api", str)
    _opt(ctx, node, "main", str)
    d = _opt(ctx, node, "dependencies", list)
    if d:
        for x in d.value:
            if not (isinstance(x.value, str) or (x.is_obj and "id" in x.value)):
                ctx.err(x, "E-TYPE", "a dependency is a plugin id in quotes or { \"id\": ... }")
    return node


def deps_of(node: Node) -> set:
    out = set()
    d = node.value.get("dependencies")
    for x in (d.value if d else []):
        out.add(x.value if isinstance(x.value, str) else x.value["id"].value)
    return out


def validate_block(folder: Path, root: Path, ns: str, deps: set, local: set, refs: list, rep: Report):
    cfg = folder / "config.json"
    ctx = Ctx(rep, cfg, root)
    node = _load(ctx)
    if node is None: return None
    if not node.is_obj:
        ctx.err(node, "E-TYPE", "config.json must be an object { }"); return None
    _schema(ctx, node); _unknown_keys(ctx, node, BLOCK_KEYS)
    i = _need(ctx, node, "id", str)
    if i and not ID_RE.match(i.value):
        ctx.err(i, "E-ID", f"id \"{i.value}\" is not valid", "Use lowercase letters, digits and _ only (for example \"ruby_block\").")
    _need(ctx, node, "name", str)
    t = _need(ctx, node, "template", str)
    template = t.value if t and _enum(ctx, t, BLOCK_TEMPLATES, "block template") else None
    if template and template not in BUILDABLE_BLOCK_TEMPLATES:
        ctx.warn(t, "W-TEMPLATE-LATER", f"template \"{template}\" is valid but not built yet: {TEMPLATE_LATER.get(template, 'planned for Phase 3')}; this block is skipped for now", "Use one of: " + ", ".join(BUILDABLE_BLOCK_TEMPLATES) + ".")
    tex = _opt(ctx, node, "textures", dict)
    if template != "custom_model":
        if tex is None:
            ctx.err(node, "E-REQUIRED", "missing required field \"textures\"", "Add \"textures\": { \"all\": \"texture.png\" }.")
        else:
            for face, fn in tex.value.items():
                if face not in FACES:
                    s = suggest(face, FACES)
                    ctx.err(tex.key_lines[face], "E-FACE", f"unknown texture face \"{face}\"", f"Did you mean \"{s}\"?" if s else f"Faces: {', '.join(FACES)}.")
                    continue
                r = _png(ctx, folder, fn, f"texture \"{face}\"")
                if r and r[1][0] != r[1][1] and r[1][0] % r[1][1] != 0:
                    ctx.err(fn, "E-TEXTURE", f"block texture is {r[1][0]}x{r[1][1]}; it must be square", "Resize to a square image such as 16x16.")
            covered = set(tex.value)
            if "all" not in covered:
                missing = [f for f in ("top", "bottom") if f not in covered]
                if "side" not in covered and not {"north", "south", "east", "west"} <= covered:
                    missing.append("side")
                if missing:
                    ctx.err(tex, "E-TEXTURE", f"textures has no \"all\" and is missing: {', '.join(missing)}", "Add \"all\" or give top, bottom and side.")
    hb = _opt(ctx, node, "hitbox", dict)
    if hb:
        ht = _need(ctx, hb, "type", str)
        if ht: _enum(ctx, ht, ["template", "box", "boxes"], "hitbox type")
        bx = _opt(ctx, hb, "boxes", list)
        if bx:
            for b in bx.value:
                vals = [x.value for x in b.value] if b.is_arr else []
                if len(vals) != 6 or not all(_is(x, float) and 0 <= x <= 16 for x in vals) or vals[0] >= vals[3] or vals[1] >= vals[4] or vals[2] >= vals[5]:
                    ctx.err(b, "E-HITBOX", "a box is [x1,y1,z1,x2,y2,z2] in 0-16 units with each first number smaller than its second", "Full block: [0,0,0,16,16,16].")
        for k in ("collision", "selection"): _opt(ctx, hb, k, bool)
        if template in BUILDABLE_BLOCK_TEMPLATES and template != "cube" and ht and ht.value in ("box", "boxes"):
            ctx.warn(hb, "W-HITBOX-IGNORED", f"the {template} template has a fixed shape, so \"hitbox\" {ht.value} is ignored", "Remove \"hitbox\", or use template \"cube\" with your own boxes once custom hitboxes are built.")
    st = _opt(ctx, node, "states", dict)
    if st:
        for name, spec in st.value.items():
            if not ID_RE.match(name):
                ctx.err(st.key_lines[name], "E-ID", f"state name \"{name}\" is not valid", "Use lowercase letters, digits and _.")
            if not spec.is_obj or "type" not in spec.value or spec.value["type"].value not in ("boolean", "enum", "int", "direction"):
                ctx.err(spec, "E-STATE", "a state is { \"type\": \"boolean\" | \"enum\" | \"int\" | \"direction\", ... }")
        if template in BUILDABLE_BLOCK_TEMPLATES and st.value:
            ctx.warn(st, "W-STATES-LATER", "custom block states are stored but not built until roadmap item P3-03" + ("" if template == "cube" else f" (the {template} template already has its own states, such as facing)"))
    mi = _opt(ctx, node, "mining", dict)
    if mi:
        h = _opt(ctx, mi, "hardness", float)
        if h and h.value < -1:
            ctx.err(h, "E-RANGE", "hardness must be 0 or more (-1 = unbreakable)")
        br = _opt(ctx, mi, "blast_resistance", float)
        if br and br.value < 0:
            ctx.err(br, "E-RANGE", "blast_resistance must be 0 or more")
        tl = _opt(ctx, mi, "tool", str)
        if tl: _enum(ctx, tl, TOOLS, "tool")
        tr = _opt(ctx, mi, "tier", str)
        if tr: _enum(ctx, tr, TIERS, "tier")
        _opt(ctx, mi, "requires_tool", bool)
    dr = _opt(ctx, node, "drops", dict)
    if dr:
        dt = _need(ctx, dr, "type", str)
        if dt and _enum(ctx, dt, ["self", "loot_table", "function", "none"], "drops type") and dt.value in ("loot_table", "function"):
            _need(ctx, dr, "id", str)
    so = _opt(ctx, node, "sounds", dict)
    if so and "preset" in so.value:
        _enum(ctx, so.value["preset"], SOUND_PRESETS, "sound preset")
    _opt(ctx, node, "particles", dict)
    lt = _opt(ctx, node, "light", int)
    if lt and not 0 <= lt.value <= 15:
        ctx.err(lt, "E-RANGE", "light must be 0 to 15")
    tk = _opt(ctx, node, "ticking", dict)
    if tk:
        _opt(ctx, tk, "enabled", bool)
        iv = _opt(ctx, tk, "interval", int)
        if iv and iv.value < 1: ctx.err(iv, "E-RANGE", "interval must be 1 or more ticks")
    ex = _opt(ctx, node, "explosion", dict)
    if ex:
        for k in ("breakable", "drops"): _opt(ctx, ex, k, bool)
    it = _opt(ctx, node, "item", dict)
    if it:
        _opt(ctx, it, "auto", bool)
        ss = _opt(ctx, it, "stack_size", int)
        if ss and not 1 <= ss.value <= 99: ctx.err(ss, "E-RANGE", "stack_size must be 1 to 99")
        ib = _opt(ctx, it, "base", str)
        if ib and not RES_RE.match(ib.value):
            ctx.err(ib, "E-RESID", f"\"{ib.value}\" is not an item id", "Use namespace:name, for example minecraft:paper. Leave \"base\" out unless you want the block item to be made of a specific vanilla item.")
    _recipes(ctx, node, ns, deps, local, refs)
    _behaviors(ctx, node)
    return node


def validate_item(folder: Path, root: Path, ns: str, deps: set, local: set, refs: list, rep: Report):
    cfg = folder / "config.json"
    ctx = Ctx(rep, cfg, root)
    node = _load(ctx)
    if node is None: return None
    if not node.is_obj:
        ctx.err(node, "E-TYPE", "config.json must be an object { }"); return None
    _schema(ctx, node); _unknown_keys(ctx, node, ITEM_KEYS)
    i = _need(ctx, node, "id", str)
    if i and not ID_RE.match(i.value):
        ctx.err(i, "E-ID", f"id \"{i.value}\" is not valid", "Use lowercase letters, digits and _ only.")
    _need(ctx, node, "name", str)
    t = _need(ctx, node, "template", str)
    template = t.value if t and _enum(ctx, t, ITEM_TEMPLATES, "item template") else None
    tex = _opt(ctx, node, "texture", str)
    if tex is None and "model" not in node.value:
        ctx.err(node, "E-REQUIRED", "an item needs \"texture\" (or \"model\")", "Add \"texture\": \"texture.png\".")
    if tex:
        r = _png(ctx, folder, tex, "texture")
        if r and r[1][0] != r[1][1] and r[1][0] % r[1][1] != 0:
            ctx.err(tex, "E-TEXTURE", f"item texture is {r[1][0]}x{r[1][1]}; it must be square (or a vertical animation strip)", "Resize to a square image such as 16x16.")
    if "model" in node.value:
        ctx.warn(node.value["model"], "W-MODEL-LATER", "custom item \"model\" files are stored but not built until Phase 3")
    ss = _opt(ctx, node, "stack_size", int)
    if ss and not 1 <= ss.value <= 99: ctx.err(ss, "E-RANGE", "stack_size must be 1 to 99")
    base = _opt(ctx, node, "base", str)
    if base and not base.value.startswith("minecraft:"):
        ctx.err(base, "E-RESID", "\"base\" must be a vanilla item such as minecraft:paper")
    stats = _opt(ctx, node, "stats", dict)
    if stats:
        for k, v in stats.value.items():
            if k == "repair_with":
                for x in (v.value if v.is_arr else []): _ref(ctx, x, ns, deps, local, refs)
            elif not _is(v.value, float):
                ctx.err(v, "E-TYPE", f"stats.{k} must be a number")
    ar = _opt(ctx, node, "armor", dict)
    if template == "armor" and ar is None:
        ctx.err(node, "E-REQUIRED", "template \"armor\" needs an \"armor\" object", "Add \"armor\": { \"slot\": \"head\", \"defense\": 3 }.")
    if ar:
        sl = _need(ctx, ar, "slot", str)
        if sl: _enum(ctx, sl, ARMOR_SLOTS, "armor slot")
    _recipes(ctx, node, ns, deps, local, refs)
    _behaviors(ctx, node)
    return node


def validate_painting(folder: Path, root: Path, ns: str, deps: set, local: set, refs: list, rep: Report):
    cfg = folder / "config.json"
    ctx = Ctx(rep, cfg, root)
    node = _load(ctx)
    if node is None: return None
    if not node.is_obj:
        ctx.err(node, "E-TYPE", "config.json must be an object { }"); return None
    _schema(ctx, node); _unknown_keys(ctx, node, PAINTING_KEYS)
    i = _need(ctx, node, "id", str)
    if i and not ID_RE.match(i.value):
        ctx.err(i, "E-ID", f"id \"{i.value}\" is not valid", "Use lowercase letters, digits and _ only.")
    _need(ctx, node, "name", str); _opt(ctx, node, "author", str)
    w, h = _need(ctx, node, "width", int), _need(ctx, node, "height", int)
    for n, nm in ((w, "width"), (h, "height")):
        if n and not 1 <= n.value <= 16:
            ctx.err(n, "E-RANGE", f"{nm} must be 1 to 16 blocks (Minecraft's limit)")
    tx = _need(ctx, node, "texture", str)
    if tx and w and h:
        r = _png(ctx, folder, tx, "texture")
        if r:
            pw, ph = r[1]
            if pw * h.value != ph * w.value:
                ctx.err(tx, "E-TEXTURE", f"painting is {w.value}x{h.value} blocks (ratio {w.value}:{h.value}) but the texture is {pw}x{ph}", f"Resize to a multiple of {w.value * 16}x{h.value * 16}, for example {w.value * 16}x{h.value * 16}.")
    _opt(ctx, node, "item", dict)
    _recipes(ctx, node, ns, deps, local, refs)
    return node
