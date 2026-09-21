"""Markdown check must ignore checkout EOL conversion without hiding content drift."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


GENERATOR = next(parent / "generate_markdown.py" for parent in Path(__file__).parents
                 if (parent / "generate_markdown.py").is_file())


class MarkdownLineEndingsTest(unittest.TestCase):
    def test_checkout_line_endings_and_content_drift_are_distinct(self):
        spec = importlib.util.spec_from_file_location("markdown_eol_generator", GENERATOR)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            module.ROOT = Path(temporary)
            module.CHECK_MODE = True
            errors = getattr(module, "CHECK_ERRORS", None)
            if errors is None:
                errors = module.CHECK_DIFFERENCES
            errors.clear()
            path = module.ROOT / "README.md"
            for stored in (b"first\nsecond\n", b"first\r\nsecond\r\n"):
                with self.subTest(stored=stored):
                    path.write_bytes(stored)
                    module.write_text(path, "first\nsecond\n")
                    self.assertEqual([], errors)
                    self.assertEqual(stored, path.read_bytes())
            for stored in (b"first\nchanged\n", b"first \nsecond\n", b"first\nsecond"):
                with self.subTest(stored=stored):
                    errors.clear()
                    path.write_bytes(stored)
                    module.write_text(path, "first\nsecond\n")
                    self.assertEqual(["README.md"], errors)
                    self.assertEqual(stored, path.read_bytes())
            errors.clear()
            missing = module.ROOT / "missing.md"
            module.write_text(missing, "expected\n")
            self.assertEqual(["missing.md"], errors)
            self.assertFalse(missing.exists())


if __name__ == "__main__":
    unittest.main()
