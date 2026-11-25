"""Shape templates (P3-01): geometry, uv rules, variant contract with the server, compile output.

Regenerate the shared golden file after an intentional change:  python3 compiler/tests/test_shapes.py --update-golden
"""
import json, shutil, sys, tempfile, unittest, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from ftc import shapes                         # noqa: E402
from ftc.build import build_plugin             # noqa: E402
from ftc.diag import Report                    # noqa: E402
from test_compiler import make_plugin, png     # noqa: E402

GOLDEN = Path(__file__).resolve().parent / "golden" / "shape-variants.json"
SHAPES_EXAMPLE = ROOT.parent / "examples" / "shapes-plugin"

# Mojang's StairBlock.SHAPE_BY_STATE, index = shape ordinal * 4 + facing (south, west, north, east); each number is the
# set of upper-layer octants (1 NW, 2 NE, 4 SW, 8 SE). Written from the vanilla source; the generator derives its boxes
# by rotating the facing-north column, so this table is an independent check of that rotation.
VANILLA_STAIR_TABLE = [12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8]
_FACING_INDEX = {"south": 0, "west": 1, "north": 2, "east": 3}
_SHAPE_ORDINAL = {"straight": 0, "inner_left": 1, "inner_right": 2, "outer_left": 3, "outer_right": 4}


def golden_content() -> dict:
    return {t: shapes.variant_names(t) for t in shapes.TEMPLATES}


class ShapeGeometry(unittest.TestCase):
    def test_rotation_is_clockwise_seen_from_above(self):
        north_edge = (0, 0, 0, 16, 16, 4)                       # a plate against the north side
        self.assertEqual(shapes.rotate_box(north_edge, 1), (12, 0, 0, 16, 16, 16))   # east
        self.assertEqual(shapes.rotate_box(north_edge, 2), (0, 0, 12, 16, 16, 16))   # south
        self.assertEqual(shapes.rotate_box(north_edge, 3), (0, 0, 0, 4, 16, 16))     # west
        self.assertEqual(shapes.rotate_box(north_edge, 4), north_edge)

    def test_all_forty_stair_states_match_the_vanilla_shape_table(self):
        for half in ("bottom", "top"):
            for shape in shapes.STAIR_SHAPES:
                for facing in shapes.FACINGS:
                    boxes = shapes.stair_boxes(half, shape, facing)
                    want = VANILLA_STAIR_TABLE[_SHAPE_ORDINAL[shape] * 4 + _FACING_INDEX[facing]]
                    self.assertEqual(shapes.stair_octant_mask(boxes, half), want, (half, shape, facing))

    def test_east_straight_bottom_stair_is_the_vanilla_stairs_model(self):
        self.assertEqual(shapes.stair_boxes("bottom", "straight", "east"), [(0, 0, 0, 16, 8, 16), (8, 8, 0, 16, 16, 16)])

    def test_stairs_use_few_elements(self):
        self.assertEqual(len(shapes.stair_boxes("bottom", "straight", "north")), 2)
        self.assertEqual(len(shapes.stair_boxes("bottom", "outer_left", "north")), 2)
        self.assertEqual(len(shapes.stair_boxes("top", "inner_left", "south")), 3)   # slab + an L of three octants (two boxes)

    def test_trapdoor_open_panel_stands_opposite_the_facing(self):
        v = shapes.variants()["trapdoor"]
        self.assertEqual(v["open_north"], [(0, 0, 13, 16, 16, 16)])   # vanilla NORTH_OPEN: z 13..16
        self.assertEqual(v["open_south"], [(0, 0, 0, 16, 16, 3)])
        self.assertEqual(v["open_east"], [(0, 0, 0, 3, 16, 16)])
        self.assertEqual(v["open_west"], [(13, 0, 0, 16, 16, 16)])
        self.assertEqual(v["bottom_closed"], [(0, 0, 0, 16, 3, 16)])
        self.assertEqual(v["top_closed"], [(0, 13, 0, 16, 16, 16)])

    def test_button_boxes(self):
        v = shapes.variants()["button"]
        self.assertEqual(v["floor_north_off"], [(5, 0, 6, 11, 2, 10)])    # the vanilla button model
        self.assertEqual(v["floor_east_off"], [(6, 0, 5, 10, 2, 11)])     # vanilla FLOOR_AABB_X: long axis turns with the facing
        self.assertEqual(v["wall_north_off"], [(5, 6, 14, 11, 10, 16)])   # vanilla NORTH_AABB
        self.assertEqual(v["ceiling_north_off"], [(5, 14, 6, 11, 16, 10)])
        self.assertEqual(v["floor_north_on"], [(5, 0, 6, 11, 1, 10)])

    def test_plate_carpet_slab(self):
        self.assertEqual(shapes.variants()["pressure_plate"]["up"], [(1, 0, 1, 15, 1, 15)])
        self.assertEqual(shapes.variants()["pressure_plate"]["down"], [(1, 0, 1, 15, 0.5, 15)])
        self.assertEqual(shapes.variants()["carpet"]["carpet"], [(0, 0, 0, 16, 1, 16)])
        self.assertEqual(shapes.variants()["slab"]["top"], [(0, 8, 0, 16, 16, 16)])

    def test_shelf_has_a_back_body_and_front_panel_for_every_facing_and_state(self):
        variants = shapes.variants()["shelf"]
        self.assertEqual(len(variants), 32)
        self.assertEqual(variants["north_unconnected_off"], [
            (0, 0, 13, 16, 16, 16), (0, 0, 11, 16, 4, 13),
            (0, 12, 11, 16, 16, 13), (0, 4, 12.9, 16, 12, 13.1)])
        self.assertEqual(variants["east_right_on"], [shapes.rotate_box(box, 1) for box in variants["north_unconnected_off"]])

    def test_uv_rule_matches_vanilla_models(self):
        # numbers read from the 26.1 vanilla models: slab, stairs (second element), trapdoor open, button
        self.assertEqual(shapes.face_uv("north", (0, 0, 0, 16, 8, 16)), [0, 8, 16, 16])
        self.assertEqual(shapes.face_uv("up", (8, 8, 0, 16, 16, 16)), [8, 0, 16, 16])
        self.assertEqual(shapes.face_uv("north", (8, 8, 0, 16, 16, 16)), [0, 0, 8, 8])
        self.assertEqual(shapes.face_uv("south", (8, 8, 0, 16, 16, 16)), [8, 0, 16, 8])
        self.assertEqual(shapes.face_uv("west", (8, 8, 0, 16, 16, 16)), [0, 0, 16, 8])
        self.assertEqual(shapes.face_uv("east", (8, 8, 0, 16, 16, 16)), [0, 0, 16, 8])
        self.assertEqual(shapes.face_uv("west", (8, 8, 8, 16, 16, 16)), [8, 0, 16, 8])    # outer stairs
        self.assertEqual(shapes.face_uv("east", (8, 8, 8, 16, 16, 16)), [0, 0, 8, 8])
        self.assertEqual(shapes.face_uv("down", (0, 0, 13, 16, 16, 16)), [0, 13, 16, 16])
        self.assertEqual(shapes.face_uv("north", (5, 0, 6, 11, 2, 10)), [5, 14, 11, 16])
        self.assertEqual(shapes.face_uv("west", (5, 0, 6, 11, 2, 10)), [6, 14, 10, 16])

    def test_models_grow_by_expand_and_keep_uv_inside_the_texture(self):
        tex = {f: "x:y" for f in ("up", "down", "north", "south", "east", "west", "particle")}
        for template, table in shapes.variants().items():
            for name, boxes in table.items():
                model = shapes.model_for(boxes, tex)
                self.assertEqual(len(model["elements"]), len(boxes))
                for element, box in zip(model["elements"], boxes):
                    for i in range(3):
                        self.assertAlmostEqual(element["from"][i], box[i] - shapes.EXPAND, 4, (template, name))
                        self.assertAlmostEqual(element["to"][i], box[i + 3] + shapes.EXPAND, 4, (template, name))
                    for face in element["faces"].values():
                        self.assertTrue(all(0 <= v <= 16 for v in face["uv"]), (template, name, face))

    def test_variant_counts(self):
        counts = {t: len(v) for t, v in shapes.variants().items()}
        self.assertEqual(counts, {"slab": 3, "stairs": 40, "trapdoor": 6, "button": 24, "pressure_plate": 2, "carpet": 1, "plant": 1, "shelf": 32})

    def test_golden_variants_file_is_current(self):
        self.assertTrue(GOLDEN.is_file(), "missing golden; run: python3 compiler/tests/test_shapes.py --update-golden")
        self.assertEqual(json.loads(GOLDEN.read_text()), golden_content(),
                         "shape variants changed: BlockShapes.java must change too, then run --update-golden")


class ShapeCompile(unittest.TestCase):
    def setUp(self):
        self.td = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.td, ignore_errors=True)

    def build(self, p):
        rep = Report()
        return build_plugin(p, self.td / "out", rep), rep

    def test_shapes_example_builds_clean_with_every_variant(self):
        out, rep = self.build(SHAPES_EXAMPLE)
        self.assertTrue(rep.ok, [str(d) for d in rep.errors])
        self.assertEqual([d.code for d in rep.warnings], [])
        with zipfile.ZipFile(out) as z:
            names = set(z.namelist())
            content = json.loads(z.read("content.json"))
        self.assertEqual(len(content["blocks"]), 8)
        for block in content["blocks"]:
            cid = block["id"].split(":")[1]
            self.assertEqual(block["variants"], sorted(shapes.variants()[block["template"]]))
            self.assertIn(f"pack/assets/shapes/items/{cid}.json", names)           # the block's own item model
            self.assertIn(f"pack/assets/shapes/models/block/{cid}.json", names)    # its icon
            for variant in block["variants"]:
                self.assertIn(f"pack/assets/shapes/models/block/{cid}/{variant}.json", names)
                self.assertIn(f"pack/assets/shapes/items/{cid}/{variant}.json", names)

    def test_cube_entry_has_no_variants_and_keeps_its_model(self):
        p = make_plugin(self.td)
        out, rep = self.build(p)
        self.assertTrue(rep.ok)
        with zipfile.ZipFile(out) as z:
            block = json.loads(z.read("content.json"))["blocks"][0]
            self.assertNotIn("variants", block)
            self.assertEqual(json.loads(z.read("pack/assets/demo/models/block/rock.json"))["parent"], "minecraft:block/cube_all")

    def test_custom_model_is_still_skipped_with_a_reason(self):
        for template, reason in (("custom_model", "P3-12"),):
            d = Path(tempfile.mkdtemp(dir=self.td))
            p = make_plugin(d)
            cfg = p / "blocks" / "Rock" / "config.json"
            cfg.write_text(json.dumps({"schema": 0, "id": "rock", "name": "Rock", "template": template, "textures": {"all": "texture.png"}}))
            out, rep = self.build(p)
            self.assertTrue(rep.ok)
            warning = [w for w in rep.warnings if w.code == "W-TEMPLATE-LATER"]
            self.assertEqual(len(warning), 1)
            self.assertIn(reason, warning[0].message)
            with zipfile.ZipFile(out) as z:
                self.assertEqual(json.loads(z.read("content.json"))["blocks"], [])

    def test_fixed_shape_templates_warn_about_a_custom_hitbox(self):
        p = make_plugin(self.td)
        (p / "blocks" / "Rock" / "config.json").write_text(json.dumps({
            "schema": 0, "id": "rock", "name": "Rock", "template": "slab", "textures": {"all": "texture.png"},
            "hitbox": {"type": "boxes", "boxes": [[0, 0, 0, 16, 4, 16]]}}))
        out, rep = self.build(p)
        self.assertTrue(rep.ok)
        self.assertEqual([w.code for w in rep.warnings], ["W-HITBOX-IGNORED"])

    def test_per_face_textures_reach_the_slab_model(self):
        p = make_plugin(self.td)
        d = p / "blocks" / "Rock"
        for name in ("top.png", "bottom.png", "side.png"):
            (d / name).write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({
            "schema": 0, "id": "rock", "name": "Rock", "template": "slab",
            "textures": {"top": "top.png", "bottom": "bottom.png", "side": "side.png"}}))
        out, rep = self.build(p)
        self.assertTrue(rep.ok, [str(x) for x in rep.errors])
        with zipfile.ZipFile(out) as z:
            model = json.loads(z.read("pack/assets/demo/models/block/rock/bottom.json"))
        self.assertEqual(model["textures"]["up"], "demo:block/rock_top")
        self.assertEqual(model["textures"]["down"], "demo:block/rock_bottom")
        self.assertEqual(model["textures"]["north"], "demo:block/rock_side")
        self.assertEqual(model["textures"]["particle"], "demo:block/rock_side")


if __name__ == "__main__":
    if "--update-golden" in sys.argv:
        GOLDEN.parent.mkdir(parents=True, exist_ok=True)
        GOLDEN.write_text(json.dumps(golden_content(), indent=2, sort_keys=True) + "\n")
        print("wrote", GOLDEN)
    else:
        unittest.main()
