"""Tests for the menu.* settings in the typed registry (run: python3 -m unittest discover -s parity)."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import flowtail  # noqa: E402


def read(text: str):
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "flowtail.properties"
        path.write_text(text, encoding="utf-8")
        return flowtail.read_config(path)


class MenuSettingsTest(unittest.TestCase):
    def test_defaults_match_the_requested_look(self):
        values, errors, _ = read("")
        self.assertEqual([], errors)
        self.assertTrue(values["menu.enabled"])
        self.assertEqual("FlowTail", values["menu.label"])
        self.assertEqual("8A2BE2", values["menu.color.from"])  # purple
        self.assertEqual("E040FB", values["menu.color.to"])    # purple-pink
        self.assertTrue(values["menu.bold"])
        self.assertEqual("Custom Items", values["menu.custom-items.label"])
        self.assertEqual("creative", values["menu.custom-items.access"])

    def test_values_are_read_and_colours_normalised(self):
        values, errors, _ = read("menu.label=My Server\nmenu.color.from=ff00aa\nmenu.bold=false\nmenu.custom-items.access=op\n")
        self.assertEqual([], errors)
        self.assertEqual("My Server", values["menu.label"])
        self.assertEqual("FF00AA", values["menu.color.from"])
        self.assertFalse(values["menu.bold"])
        self.assertEqual("op", values["menu.custom-items.access"])

    def test_inline_comment_is_ignored(self):
        values, errors, _ = read("menu.label=FlowTail   # shown on the pause screen\n")
        self.assertEqual([], errors)
        self.assertEqual("FlowTail", values["menu.label"])

    def test_hash_in_colour_is_a_clear_error_not_a_silent_blank(self):
        _, errors, _ = read("menu.color.to=#E040FB\n")
        self.assertEqual(1, len(errors))
        self.assertIn("menu.color.to", errors[0])

    def test_bad_values_are_errors(self):
        for line in ("menu.color.from=GGGGGG", "menu.color.to=12345", "menu.label=", "menu.label=" + "x" * 33,
                     "menu.custom-items.access=nobody", "menu.bold=maybe", "menu.enabled=2"):
            with self.subTest(line=line):
                _, errors, _ = read(line + "\n")
                self.assertEqual(1, len(errors), errors)

    def test_generated_file_contains_every_menu_key_and_validates(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "flowtail.properties"
            flowtail.write_example(path)
            text = path.read_text(encoding="utf-8")
            for key in [s.key for s in flowtail.SETTINGS if s.key.startswith("menu.")]:
                self.assertIn(key + "=", text)
            _, errors, _ = flowtail.read_config(path)
            self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
