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
        self.assertEqual([d.code for d in rep.warnings], [])

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

    def test_deterministic_and_add_only_ids(self):
        p = make_plugin(self.td, blocks=("aaa", "bbb"))
        o1, r = self.build(p); self.assertTrue(o1)
        def pack(out, state):
            rep = Report(); info = build_pack([o1], state, out, 97, rep); return info, rep
        i1, _ = pack(self.td / "p1", self.td / "s")
        i2, _ = pack(self.td / "p2", self.td / "s")
        self.assertEqual(i1["sha1"], i2["sha1"])
        alloc = json.loads((self.td / "s/allocations.json").read_text())["blocks"]
        # add a block that sorts BEFORE the others: old slots must not move
        p2 = make_plugin(self.td / "..", pid="demo") if False else p
        d = p / "blocks" / "A0"; d.mkdir(); (d / "texture.png").write_bytes(png(16, 16))
        (d / "config.json").write_text(json.dumps({"schema": 0, "id": "a0", "name": "A0", "template": "cube", "textures": {"all": "texture.png"}}))
        o2, _ = self.build(p)
        i3, _ = pack(self.td / "p3", self.td / "s")
        alloc2 = json.loads((self.td / "s/allocations.json").read_text())["blocks"]
        for k, v in alloc.items(): self.assertEqual(alloc2[k], v)
        self.assertTrue(alloc2["demo:a0"])

    def test_removed_block_id_is_retained_as_retired(self):
        p = make_plugin(self.td, blocks=("aaa", "bbb"))
        o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "p1", 97, rep)
        known = json.loads((self.td / "s/allocations.json").read_text())["blocks"]
        shutil.rmtree(p / "blocks" / "Aaa")
        o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "p2", 97, rep)
        known2 = json.loads((self.td / "s/allocations.json").read_text())["blocks"]
        self.assertFalse(known2["demo:aaa"]); self.assertTrue(known["demo:aaa"])
        reg = json.loads((self.td / "p2/registry.json").read_text())
        self.assertEqual(reg["retired_blocks"], ["demo:aaa"])
        with zipfile.ZipFile(self.td / "p2/pack.zip") as pack:
            self.assertNotIn("assets/minecraft/blockstates/note_block.json", pack.namelist())

    def test_legacy_carrier_allocations_migrate_to_ids(self):
        p = make_plugin(self.td); archive, _ = self.build(p)
        state = self.td / "legacy-state"; state.mkdir()
        legacy = {"schema": 0, "pools": {"cube.note_block": {"block": "minecraft:note_block", "slots": {
            "old:stone": {"state": {"instrument": "harp", "note": "4", "powered": "true"}, "retired": False}
        }}}}
        (state / "allocations.json").write_text(json.dumps(legacy))
        rep = Report(); build_pack([archive], state, self.td / "legacy-pack", 97, rep)
        migrated = json.loads((state / "allocations.json").read_text())
        self.assertNotIn("pools", migrated)
        self.assertFalse(migrated["blocks"]["old:stone"])
        self.assertEqual(json.loads((self.td / "legacy-pack/registry.json").read_text())["retired_blocks"], ["old:stone"])

    def test_pack_never_overrides_vanilla_blockstates(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", 97, rep)
        with zipfile.ZipFile(self.td / "pk/pack.zip") as pack:
            self.assertFalse(any(name.startswith("assets/minecraft/blockstates/") for name in pack.namelist()))
            self.assertFalse(any(name.startswith("assets/minecraft/models/block/") for name in pack.namelist()))

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

    def test_plugins_cannot_override_vanilla_block_assets(self):
        p = make_plugin(self.td); archive, _ = self.build(p)
        with zipfile.ZipFile(archive) as z: files = {name: z.read(name) for name in z.namelist()}
        manifest = json.loads(files["manifest.json"])
        path = "pack/assets/minecraft/blockstates/note_block.json"
        files[path] = b"{}"
        import hashlib
        manifest["files"][path] = hashlib.sha256(files[path]).hexdigest()
        files["manifest.json"] = json.dumps(manifest).encode()
        archive.unlink()
        with zipfile.ZipFile(archive, "w") as z:
            for name, data in files.items(): z.writestr(name, data)
        rep = Report(); info = build_pack([archive], self.td / "s", self.td / "pk", 97, rep)
        self.assertFalse(info)
        self.assertIn("E-VANILLA-BLOCK-OVERRIDE", [d.code for d in rep.errors])

    def test_damaged_plugin_detected(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        with zipfile.ZipFile(o) as z: files = {n: z.read(n) for n in z.namelist()}
        files["content.json"] += b" "
        o.unlink()
        with zipfile.ZipFile(o, "w") as z:
            for n, d in files.items(): z.writestr(n, d)
        with self.assertRaises(SystemExit): build_pack([o], self.td / "s", self.td / "pk", 97, Report())

    def test_packs_never_generate_vanilla_blockstates(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", 97, rep)
        with zipfile.ZipFile(self.td / "pk/pack.zip") as pack:
            self.assertNotIn("assets/minecraft/blockstates/note_block.json", pack.namelist())

    def test_resource_pack_minor_format_is_preserved(self):
        p = make_plugin(self.td); o, _ = self.build(p)
        rep = Report(); build_pack([o], self.td / "s", self.td / "pk", [97, 1], rep)
        meta = json.loads(zipfile.ZipFile(self.td / "pk/pack.zip").read("pack.mcmeta"))
        self.assertEqual(meta["pack"]["min_format"], [97, 1])
        self.assertEqual(meta["pack"]["max_format"], [97, 1])
        self.assertEqual(json.loads((self.td / "pk/registry.json").read_text())["pack_format"], [97, 1])

    def _block_config(self, p, **extra):
        cfg = p / "blocks" / "Rock" / "config.json"
        data = json.loads(cfg.read_text())
        data.update(extra)
        cfg.write_text(json.dumps(data, indent=1))
        return cfg

    def _content(self, out):
        with zipfile.ZipFile(out) as z:
            return json.loads(z.read("content.json"))

    def test_block_registry_has_no_carrier_inheritance(self):
        p = make_plugin(self.td)
        out, rep = self.build(p)
        self.assertTrue(rep.ok, [str(d) for d in rep.errors])
        block = self._content(out)["blocks"][0]
        self.assertNotIn("carrier_inherit", block)
        self.assertNotIn("base", block["item"])

    def test_legacy_carrier_inherit_is_ignored_but_item_base_is_kept(self):
        p = make_plugin(self.td)
        self._block_config(p, carrier_inherit=["note_sound"], item={"auto": True, "base": "minecraft:note_block"})
        out, rep = self.build(p)
        self.assertTrue(rep.ok, [str(d) for d in rep.errors])
        self.assertEqual([d.code for d in rep.warnings], ["W-UNKNOWN-KEY"])
        block = self._content(out)["blocks"][0]
        self.assertNotIn("carrier_inherit", block)
        self.assertEqual(block["item"]["base"], "minecraft:note_block")

    def test_legacy_carrier_inherit_values_do_not_affect_vanilla_behavior(self):
        p = make_plugin(self.td)
        cfg = self._block_config(p, carrier_inherit=["note_sond", 5], item={"base": "Paper"})
        out, rep = self.build(p)
        self.assertIsNone(out)
        codes = [d.code for d in rep.errors]
        self.assertIn("E-RESID", codes)
        self.assertTrue(all(d.fix for d in rep.errors))
        self.assertIn("W-UNKNOWN-KEY", [d.code for d in rep.warnings])

    def test_legacy_carrier_inherit_warns_once(self):
        p = make_plugin(self.td)
        self._block_config(p, carrier_inherit=["note_sound", "note_sound"])
        out, rep = self.build(p)
        self.assertTrue(rep.ok)
        self.assertEqual([d.code for d in rep.warnings], ["W-UNKNOWN-KEY"])

    def test_block_without_auto_item_still_gets_display_item_model(self):
        # The display entity renders item_model "<ns>:<id>" even when the block has no giveable item.
        p = make_plugin(self.td)
        self._block_config(p, item={"auto": False})
        out, rep = self.build(p)
        self.assertTrue(rep.ok, [str(d) for d in rep.errors])
        with zipfile.ZipFile(out) as z:
            names = z.namelist()
        item_defs = [n for n in names if n.startswith("pack/assets/") and "/items/" in n]
        self.assertEqual(len(item_defs), 1, names)
        self.assertFalse(self._content(out)["blocks"][0]["item"]["auto"])


if __name__ == "__main__":
    unittest.main()
