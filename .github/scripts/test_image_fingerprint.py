import tempfile
import unittest
from pathlib import Path

import image_fingerprint as subject


class FingerprintTest(unittest.TestCase):
    def test_file_and_base_changes_invalidate_image(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "Dockerfile").write_text("FROM node:24-alpine\n", encoding="utf-8")
            (root / "data").mkdir()
            source = root / "data" / "item.txt"
            source.write_text("first", encoding="utf-8")

            original = subject.ROOT, subject.INPUTS, subject.DOCKERFILES
            try:
                subject.ROOT = root
                subject.INPUTS = {"demo": ["Dockerfile", "data"]}
                subject.DOCKERFILES = {"demo": "Dockerfile"}
                first = subject.fingerprint("demo", {"node:24-alpine": b"base one"})
                (root / "workflow").write_text("new test step\n", encoding="utf-8")
                self.assertEqual(first, subject.fingerprint("demo", {"node:24-alpine": b"base one"}))
                source.write_text("second", encoding="utf-8")
                changed_file = subject.fingerprint("demo", {"node:24-alpine": b"base one"})
                changed_base = subject.fingerprint("demo", {"node:24-alpine": b"base two"})
                self.assertNotEqual(first, changed_file)
                self.assertNotEqual(changed_file, changed_base)
            finally:
                subject.ROOT, subject.INPUTS, subject.DOCKERFILES = original


if __name__ == "__main__":
    unittest.main()
