import importlib.util
import os
from pathlib import Path
import tempfile
import unittest


class PackArchiveTest(unittest.TestCase):
    def test_file_timestamps_do_not_change_archive(self):
        script = Path(__file__).resolve().parents[2] / "minecraft/main/respack/build-main-pack.py"
        spec = importlib.util.spec_from_file_location("build_main_pack", script)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "pack"
            source.mkdir()
            file = source / "pack.mcmeta"
            file.write_text('{"pack":{}}', encoding="utf-8")
            first, second = root / "first.zip", root / "second.zip"
            module.write_pack_archive(source, first)
            os.utime(file, (1_000_000_000, 1_000_000_000))
            module.write_pack_archive(source, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())


if __name__ == "__main__":
    unittest.main()
