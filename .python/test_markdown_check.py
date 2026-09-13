"""The check path must detect drift and never create or rewrite output files."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("generator_under_test", Path(__file__).with_name("generate_markdown.py"))
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)

class ReadOnlyCheckTest(unittest.TestCase):
    def test_existing_drift_is_reported_without_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            generator.ROOT = Path(directory)
            generator.CHECK_MODE = True
            generator.CHECK_DIFFERENCES.clear()
            path = generator.ROOT / "README.md"
            path.write_bytes(b"old content")
            before = path.stat().st_mtime_ns
            generator.write_text(path, "new content")
            self.assertEqual(b"old content", path.read_bytes())
            self.assertEqual(before, path.stat().st_mtime_ns)
            self.assertEqual(["README.md"], generator.CHECK_DIFFERENCES)

    def test_missing_output_is_not_created(self):
        with tempfile.TemporaryDirectory() as directory:
            generator.ROOT = Path(directory)
            generator.CHECK_MODE = True
            generator.CHECK_DIFFERENCES.clear()
            path = generator.ROOT / "missing" / "README.md"
            generator.write_text(path, "new content")
            self.assertFalse(path.parent.exists())
            self.assertEqual(1, len(generator.CHECK_DIFFERENCES))

if __name__ == "__main__":
    unittest.main()
