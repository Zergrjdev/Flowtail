"""Shape templates (P3-01): the visible geometry of slab, stairs, trapdoor, button, pressure plate, carpet and plant.

How a shaped block works (ADR-028): the server places a REAL vanilla block with the right collision and selection shape
(a slab, a stair...) and covers it with an item display that shows the FlowTail model. The display must not z-fight
with the vanilla block's own texture underneath, so every box of every model is drawn EXPAND pixels larger than the
vanilla box on all sides, with the texture coordinates of the un-grown box (so nothing stretches or bleeds).

Orientation rule: all geometry is baked per facing (no display rotation at runtime). The baseline is facing NORTH and
`rotate_box` turns it clockwise seen from above, exactly as vanilla blockstate `y` rotation does, with texture
coordinates recomputed for the rotated box (the same effect as vanilla `uvlock`).

Variant names are a contract with the server (`BlockShapes.java`): the server chooses a variant name from a block's state
and the pack must contain a model + item definition with exactly that name. `compiler/tests/golden/shape-variants.json`
is checked by both test suites, and the registry lists every variant so the server can verify it at startup.

Sources: vanilla model and shape numbers were compared against the 26.1 vanilla models (slab, stairs, inner/outer
stairs, trapdoor, button, pressure plate, carpet, cross) and Mojang's StairBlock shape table. Nothing from Mojang is copied
into this repository: only dimensions and the uv rules.
"""
from __future__ import annotations

FACINGS = ["north", "east", "south", "west"]            # clockwise seen from above; index = clockwise quarter turns
EXPAND = 0.016                                          # pixels = 0.001 block, the "0.001 bigger" rule

TEMPLATES = ["slab", "stairs", "trapdoor", "button", "pressure_plate", "carpet", "plant", "shelf"]
SHAPED_TEMPLATES = TEMPLATES                            # templates that are built from variants (cube is not)

Box = tuple  # (x1, y1, z1, x2, y2, z2) in 16ths of a block


def rotate_box(b: Box, quarter_turns: int) -> Box:
    """Rotate a box clockwise (north -> east) about the block centre. One turn maps (x, z) to (16 - z, x)."""
    x1, y1, z1, x2, y2, z2 = b
    for _ in range(quarter_turns % 4):
        nx1, nx2 = 16 - z2, 16 - z1
        nz1, nz2 = x1, x2
        x1, x2, z1, z2 = nx1, nx2, nz1, nz2
    return (x1, y1, z1, x2, y2, z2)


def merge_boxes(boxes: list[Box]) -> list[Box]:
    """Join boxes that touch with the same cross-section, so a straight stair is two boxes and not three."""
    boxes = list(boxes)
    changed = True
    while changed:
        changed = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                m = _try_merge(boxes[i], boxes[j])
                if m is not None:
                    boxes[i] = m
                    del boxes[j]
                    changed = True
                    break
            if changed:
                break
    return sorted(boxes)


def _try_merge(a: Box, b: Box) -> Box | None:
    for axis in range(3):
        others = [k for k in range(3) if k != axis]
        if all(a[k] == b[k] and a[k + 3] == b[k + 3] for k in others):
            if a[axis + 3] == b[axis]:
                lo, hi = a, b
            elif b[axis + 3] == a[axis]:
                lo, hi = b, a
            else:
                continue
            out = list(lo)
            out[axis + 3] = hi[axis + 3]
            return tuple(out)
    return None


# --- stairs ---------------------------------------------------------------------------------------------------------
# Octants of the upper (or lower, for half=top) layer, as vanilla StairBlock numbers them: 1 NW, 2 NE, 4 SW, 8 SE.
# Masks for facing NORTH. Vanilla's table SHAPE_BY_STATE lists facing south/west/north/east; the north column of that
# table (3, 7, 11, 1, 2) is what is written here, and the test compares all 40 states with the whole vanilla table.
STAIR_SHAPES = ["straight", "inner_left", "inner_right", "outer_left", "outer_right"]
_STAIR_MASK_NORTH = {"straight": 3, "inner_left": 7, "inner_right": 11, "outer_left": 1, "outer_right": 2}
_OCTANT = {1: (0, 0, 0, 8, 8, 8), 2: (8, 0, 0, 16, 8, 8), 4: (0, 0, 8, 8, 8, 16), 8: (8, 0, 8, 16, 8, 16)}


def stair_boxes(half: str, shape: str, facing: str) -> list[Box]:
    turns = FACINGS.index(facing)
    y_base = (0, 8) if half == "bottom" else (8, 16)      # the full slab layer
    y_octant = (8, 16) if half == "bottom" else (0, 8)    # the layer with the steps
    boxes = [(0, y_base[0], 0, 16, y_base[1], 16)]
    mask = _STAIR_MASK_NORTH[shape]
    for bit, o in _OCTANT.items():
        if mask & bit:
            boxes.append((o[0], y_octant[0], o[2], o[3], y_octant[1], o[5]))
    return merge_boxes([rotate_box(b, turns) for b in boxes])


def stair_octant_mask(boxes: list[Box], half: str) -> int:
    """For tests: which upper-layer octants a stair's boxes cover (same numbering as vanilla)."""
    y_octant = (8, 16) if half == "bottom" else (0, 8)
    mask = 0
    for bit, o in _OCTANT.items():
        cx, cz = (o[0] + o[3]) / 2, (o[2] + o[5]) / 2
        cy = (y_octant[0] + y_octant[1]) / 2
        if any(b[0] < cx < b[3] and b[1] < cy < b[4] and b[2] < cz < b[5] for b in boxes):
            mask |= bit
    return mask


# --- other templates ------------------------------------------------------------------------------------------------
def _button_box(face: str, pressed: bool) -> Box:
    d = 1 if pressed else 2                                # depth
    if face == "floor":
        return (5, 0, 6, 11, d, 10)
    if face == "ceiling":
        return (5, 16 - d, 6, 11, 16, 10)
    return (5, 6, 16 - d, 11, 10, 16)                      # wall, facing north: attached to the block on its south side


def variants() -> dict[str, dict[str, list[Box]]]:
    """template -> {variant name: boxes}. The variant names are the contract with BlockShapes.java."""
    out: dict[str, dict[str, list[Box]]] = {}
    out["slab"] = {"bottom": [(0, 0, 0, 16, 8, 16)], "top": [(0, 8, 0, 16, 16, 16)], "double": [(0, 0, 0, 16, 16, 16)]}
    stairs = {}
    for half in ("bottom", "top"):
        for shape in STAIR_SHAPES:
            for facing in FACINGS:
                stairs[f"{half}_{shape}_{facing}"] = stair_boxes(half, shape, facing)
    out["stairs"] = stairs
    trap = {"bottom_closed": [(0, 0, 0, 16, 3, 16)], "top_closed": [(0, 13, 0, 16, 16, 16)]}
    for facing in FACINGS:   # open: the panel stands against the side OPPOSITE the facing (vanilla NORTH_OPEN = z 13..16)
        trap[f"open_{facing}"] = [rotate_box((0, 0, 13, 16, 16, 16), FACINGS.index(facing))]
    out["trapdoor"] = trap
    button = {}
    for face in ("floor", "wall", "ceiling"):
        for facing in FACINGS:
            for pressed in (False, True):
                button[f"{face}_{facing}_{'on' if pressed else 'off'}"] = [rotate_box(_button_box(face, pressed), FACINGS.index(facing))]
    out["button"] = button
    out["pressure_plate"] = {"up": [(1, 0, 1, 15, 1, 15)], "down": [(1, 0, 1, 15, 0.5, 15)]}
    out["carpet"] = {"carpet": [(0, 0, 0, 16, 1, 16)]}
    out["plant"] = {"plant": []}                           # drawn with the vanilla cross model, not boxes
    shelf = {}
    # A wall-mounted three-slot shelf display: rear body, upper/lower lips, and the thin front panel.
    # All runtime collision and state storage belongs to FlowTail; no vanilla block is used as a carrier.
    for facing in FACINGS:
        for chain in ("unconnected", "left", "center", "right"):
            for powered in ("off", "on"):
                name = f"{facing}_{chain}_{powered}"
                shelf[name] = [
                    (0, 0, 13, 16, 16, 16),
                    (0, 0, 11, 16, 4, 13),
                    (0, 12, 11, 16, 16, 13),
                    (0, 4, 12.9, 16, 12, 13.1),
                ] if facing == "north" else [
                    rotate_box(b, FACINGS.index(facing)) for b in [
                        (0, 0, 13, 16, 16, 16),
                        (0, 0, 11, 16, 4, 13),
                        (0, 12, 11, 16, 16, 13),
                        (0, 4, 12.9, 16, 12, 13.1),
                    ]
                ]
    out["shelf"] = shelf
    return out


# The variant used as the inventory icon / the item's own model.
ICON_VARIANT = {"slab": "bottom", "stairs": "bottom_straight_east", "trapdoor": "bottom_closed",
                "button": "floor_north_off", "pressure_plate": "up", "carpet": "carpet", "plant": "plant",
                "shelf": "north_unconnected_off"}

_DIRECTION_FACES = ["up", "down", "north", "south", "east", "west"]


def face_uv(face: str, b: Box) -> list[float]:
    """Vanilla's uv rule for a face of the box b (checked against the vanilla slab, stairs, trapdoor and button models)."""
    x1, y1, z1, x2, y2, z2 = b
    return {
        "up": [x1, z1, x2, z2],
        "down": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def _n(v: float):
    """Whole numbers stay integers in the JSON so files are stable and readable."""
    v = round(v, 4)
    return int(v) if v == int(v) else v


def model_for(boxes: list[Box], textures: dict[str, str]) -> dict:
    """Model JSON: parent minecraft:block/block (gives the inventory/hand display transforms) + one element per box,
    each grown by EXPAND on every side, uv from the un-grown box."""
    elements = []
    for b in boxes:
        grown_from = [_n(b[0] - EXPAND), _n(b[1] - EXPAND), _n(b[2] - EXPAND)]
        grown_to = [_n(b[3] + EXPAND), _n(b[4] + EXPAND), _n(b[5] + EXPAND)]
        faces = {f: {"uv": [_n(v) for v in face_uv(f, b)], "texture": f"#{f}"} for f in _DIRECTION_FACES}
        elements.append({"from": grown_from, "to": grown_to, "faces": faces})
    return {"parent": "minecraft:block/block", "textures": dict(textures), "elements": elements}


def plant_model(texture_ref: str) -> dict:
    return {"parent": "minecraft:block/cross", "textures": {"cross": texture_ref}}


def face_textures(ns: str, bid: str, tex: dict) -> dict[str, str]:
    """Model texture variables (#up #down #north ... #particle) from the block's configured faces."""
    ref = lambda f: f"{ns}:block/{bid}" if f == "all" else f"{ns}:block/{bid}_{f}"

    def pick(*chain):
        for c in chain:
            if c in tex:
                return ref(c)
        return ref("all") if "all" in tex else None

    out = {"up": pick("top"), "down": pick("bottom")}
    for d in ("north", "south", "east", "west"):
        out[d] = pick(d, "side")
    out["particle"] = pick("particle", "side", "top")
    return {k: v for k, v in out.items() if v}


def variant_names(template: str) -> list[str]:
    return sorted(variants()[template])
