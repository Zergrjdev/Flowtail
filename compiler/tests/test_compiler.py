import json, shutil, struct, sys, tempfile, unittest, zlib, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from ftc.build import build_plugin          # noqa: E402
from ftc.diag import Report                 # noqa: E402
from ftc.jsonpos import parse, JsonError    # noqa: E402
from ftc.pack import build_pack             # noqa: E402

EXAMPLE = ROOT.parent / "examples" / "example-plugin"


def png(w, h):
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    raw = b"".join(b"\x00" + b"\xff\x00\x00\xff" * w for _ in range(h))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def make_plugin(td: Path, pid="demo", blocks=("rock",), extra=None):
    p = td / pid
    (p).mkdir()
    (p / "plugin.json").write_text(json.dumps({"schema": 0, "id": pid, "name": "Demo", "version": "1.0.0", "authors": ["me"], "api": "0.x"}))
    for b in blocks:
        d = p / "blocks" / b.title(); d.mkdir(parents=True)
        (d / "texture.png").write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({"schema": 0, "id": b, "name": b.title(), "template": "cube", "textures": {"all": "texture.png"}}))
    return p


class T(unittest.TestCase):
    def setUp(self):
        self.td = Path(tempfile.mkdtemp())
    def tearDown(self):
        shutil.rmtree(self.td, ignore_errors=True)

    def build(self, p):
        rep = Report(); out = build_plugin(p, self.td / "out", rep); return out, rep

    def test_json_positions(self):
        n = parse('{\n "a": 1,\n "b": {\n  "c": [1,\n 2]\n }\n}')
        self.assertEqual(n.key_lines["a"], 2); self.assertEqual(n.value["b"].key_lines["c"], 4)
        with self.assertRaises(JsonError) as e: parse('{\n "a": 1,\n}')
        self.assertEqual(e.exception.line, 3)
        with self.assertRaises(JsonError): parse('{"a":1 "b":2}')

    def test_example_builds(self):
        out, rep = self.build(EXAMPLE)
        self.assertTrue(rep.ok, [str(d) for d in rep.errors]); self.assertTrue(out.is_file())
        self.assertEqual([d.code for d in rep.warnings], ["W-TEMPLATE-LATER"])

    def test_error_has_file_line_and_fix(self):
        p = make_plugin(self.td)
        cfg = p / "blocks" / "Rock" / "config.json"
        cfg.write_text('{\n "schema": 0,\n "id": "Rock Block",\n "name": "Rock",\n "template": "cubee",\n "textures": {"all": "nope.png"},\n "light": 99\n}')
        out, rep = self.build(p)
        self.assertIsNone(out)
        got = {(d.code, d.line) for d in rep.errors}
        self.assertIn(("E-ID", 3), got); self.assertIn(("E-ENUM", 5), got); self.assertIn(("E-TEXTURE", 6), got); self.assertIn(("E-RANGE", 7), got)
        self.assertTrue(all(d.fix for d in rep.errors))
        self.assertTrue(any("Did you mean \"cube\"" in d.fix for d in rep.errors))
        self.assertTrue(all(d.file.endswith("blocks/Rock/config.json") for d in rep.errors))

    def test_bad_json_reports_line(self):
        p = make_plugin(self.td)
        (p / "blocks" / "Rock" / "config.json").write_text('{\n "schema": 0,\n "id": "rock",\n}')
        _, rep = self.build(p)
        self.assertEqual([(d.code, d.line) for d in rep.errors], [("E-JSON", 4)])

    def test_typo_key_warns_with_suggestion(self):
        p = make_plugin(self.td)
        c = json.loads((p / "blocks/Rock/config.json").read_text()); c["minning"] = {}
        (p / "blocks/Rock/config.json").write_text(json.dumps(c))
        out, rep = self.build(p)
        self.assertTrue(out); self.assertTrue(any(d.code == "W-UNKNOWN-KEY" and "mining" in d.fix for d in rep.warnings))

    def test_recipe_reference_and_duplicates(self):
        p = make_plugin(self.td, blocks=("rock", "gem"))
        c = json.loads((p / "blocks/Rock/config.json").read_text())
        c["recipes"] = [{"type": "shapeless", "ingredients": ["demo:missing", "minecraft:stick"]}]
        (p / "blocks/Rock/config.json").write_text(json.dumps(c, indent=1))
        d = p / "items" / "Gem"; d.mkdir(parents=True); (d / "texture.png").write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({"schema": 0, "id": "gem", "name": "Gem", "template": "generic", "texture": "texture.png"}))
        out, rep = self.build(p)
        codes = {d.code for d in rep.errors}
        self.assertEqual(codes, {"E-REFERENCE", "E-DUPLICATE"})

    def test_painting_ratio(self):
        p = make_plugin(self.td, blocks=())
        d = p / "paintings" / "Wide"; d.mkdir(parents=True); (d / "texture.png").write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({"schema": 0, "id": "wide", "name": "Wide", "texture": "texture.png", "width": 2, "height": 1}))
        _, rep = self.build(p)
        self.assertEqual([x.code for x in rep.errors], ["E-TEXTURE"]); self.assertIn("32x16", rep.errors[0].fix)

    def test_deterministic_and_stable_slots(self):
        p = make_plugin(self.td, blocks=("aaa", "bbb"))
        o1, r = self.build(p); self.assertTrue(o1)
        def pack(out, state):
            rep = Report(); info = build_pack([o1], state, out, 97, rep); return info, rep
        i1, _ = pack(self.td / "p1", self.td / "s")
        i2, _ = pack(self.td / "p2", self.td / "s")
        self.assertEqual(i1["sha1"], i2["sha1"])
        alloc = json.loads((self.td / "s/allocations.json").read_text())["pools"]["cube.note_block"]["slots"]
        first = {k: v["state"] for k, v in alloc.items()}
        # add a block that sorts BEFORE the others: old slots must not move
        p2 = make_plugin(self.td / "..", pid="demo") if False else p
        d = p / "blocks" / "A0"; d.mkdir(); (d / "texture.png").write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({"schema": 0, "id": "a0", "name": "A0", "template": "cube", "textures": {"all": "texture.png"}}))
        o2, _ = self.build(p)
        i3, _ = pack(self.td / "p3", self.td / "s")
        alloc2 = json.loads((self.td / "s/allocations.json").read_text())["pools"]["cube.note_block"]["slots"]
        for k, v in first.items(): self.assertEqual(alloc2[k]["state"], v)
        self.assertNotIn(alloc2["demo:a0"]["state"], first.values())

    def test_removed_block_keeps_slot_and_is_never_reused(self):
        p = make_plugin(self.td, blocks=("aaa", "bbb"))
        o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "p1", 97, rep)
        slots = json.loads((self.td / "s/allocations.json").read_text())["pools"]["cube.note_block"]["slots"]
        shutil.rmtree(p / "blocks" / "Aaa")
        o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "p2", 97, rep)
        self.assertTrue(any(d.code == "W-BLOCK-RETIRED" for d in rep.warnings))
        slots2 = json.loads((self.td / "s/allocations.json").read_text())["pools"]["cube.note_block"]["slots"]
        self.assertTrue(slots2["demo:aaa"]["retired"]); self.assertEqual(slots2["demo:aaa"]["state"], slots["demo:aaa"]["state"])
        reg = json.loads((self.td / "p2/registry.json").read_text())
        self.assertEqual(reg["retired_blocks"], ["demo:aaa"])
        bs = json.load(zipfile.ZipFile(self.td / "p2/pack.zip").open("assets/minecraft/blockstates/note_block.json"))["variants"]
        k = ",".join(f"{n}={slots['demo:aaa']['state'][n]}" for n in sorted(slots["demo:aaa"]["state"]))
        self.assertEqual(bs[k]["model"], "minecraft:block/barrier")

    def test_blockstates_cover_every_state_and_keep_vanilla(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", 97, rep)
        bs = json.load(zipfile.ZipFile(self.td / "pk/pack.zip").open("assets/minecraft/blockstates/note_block.json"))["variants"]
        self.assertEqual(len(bs), 27 * 25 * 2)
        self.assertEqual(bs["instrument=harp,note=0,powered=false"]["model"], "minecraft:block/note_block")
        self.assertEqual(sum(1 for v in bs.values() if v["model"] == "demo:block/rock"), 1)

    def test_pack_conflict_reported(self):
        a = make_plugin(self.td, "pa", ("x",)); b = make_plugin(self.td, "pb", ("y",))
        oa, _ = self.build(a); ob, _ = self.build(b)
        # inject the same minecraft-namespace path with different bytes into both
        for o, data in ((oa, b"1"), (ob, b"2")):
            with zipfile.ZipFile(o) as z: files = {n: z.read(n) for n in z.namelist()}
            m = json.loads(files["manifest.json"]); files["pack/assets/minecraft/lang/en_us.json"] = data
            import hashlib; m["files"]["pack/assets/minecraft/lang/en_us.json"] = hashlib.sha256(data).hexdigest()
            files["manifest.json"] = json.dumps(m).encode()
            o.unlink()
            with zipfile.ZipFile(o, "w") as z:
                for n, d in files.items(): z.writestr(n, d)
        rep = Report(); info = build_pack([oa, ob], self.td / "s", self.td / "pk", 97, rep)
        self.assertTrue(info); self.assertEqual([d.code for d in rep.warnings if d.code == "W-PACK-CONFLICT"], ["W-PACK-CONFLICT"])
        self.assertEqual(zipfile.ZipFile(self.td / "pk/pack.zip").read("assets/minecraft/lang/en_us.json"), b"1")
        rep = Report(); info = build_pack([oa, ob], self.td / "s2", self.td / "pk2", 97, rep, on_conflict="error")
        self.assertFalse(info)

    def test_damaged_plugin_detected(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        with zipfile.ZipFile(o) as z: files = {n: z.read(n) for n in z.namelist()}
        files["content.json"] += b" "
        o.unlink()
        with zipfile.ZipFile(o, "w") as z:
            for n, d in files.items(): z.writestr(n, d)
        with self.assertRaises(SystemExit): build_pack([o], self.td / "s", self.td / "pk", 97, Report())

    def test_blocks_report_is_used(self):
        report = {"minecraft:note_block": {"properties": {"instrument": ["harp", "bass"], "note": ["0", "1"], "powered": ["true", "false"]}}}
        rp = self.td / "blocks.json"; rp.write_text(json.dumps(report))
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", 97, rep, blocks_report=rp)
        self.assertFalse(any(d.code == "W-CARRIER-UNVERIFIED" for d in rep.warnings))
        bs = json.load(zipfile.ZipFile(self.td / "pk/pack.zip").open("assets/minecraft/blockstates/note_block.json"))["variants"]
        self.assertEqual(len(bs), 8)

    def test_resource_pack_minor_format_is_preserved(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", [97, 1], rep)
        meta = json.loads(zipfile.ZipFile(self.td / "pk/pack.zip").read("pack.mcmeta"))
        self.assertEqual(meta["pack"]["min_format"], [97, 1])
        self.assertEqual(meta["pack"]["max_format"], [97, 1])
        self.assertEqual(json.loads((self.td / "pk/registry.json").read_text())["pack_format"], [97, 1])

    def test_note_block_fallback_includes_26_3_instruments(self):
        from ftc.carriers import FALLBACK_NOTE_BLOCK
        self.assertEqual(len(FALLBACK_NOTE_BLOCK["instrument"]), 27)
        self.assertEqual(FALLBACK_NOTE_BLOCK["instrument"][16:20], [
            "trumpet", "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered"
        ])


if __name__ == "__main__":
    unittest.main()
