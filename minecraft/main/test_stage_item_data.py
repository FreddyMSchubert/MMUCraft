import json
import tempfile
import unittest
from pathlib import Path

from stage_item_data import validate_hopper_filter_group_references


class HopperFilterGroupReferencesTest(unittest.TestCase):
	def test_fake_item_must_exist(self):
		with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as directory:
			root = Path(directory)
			item = root / "data/data/items/example/item.json"
			group = root / "data/data/hopper_filter_groups/example.json"
			item.parent.mkdir(parents=True)
			group.parent.mkdir(parents=True)
			item.write_text(json.dumps({"id": "known"}), encoding="utf-8")
			group.write_text(json.dumps({"items": ["mainmod:missing"]}), encoding="utf-8")

			with self.assertRaisesRegex(ValueError, "Unknown fake item 'mainmod:missing'"):
				validate_hopper_filter_group_references(root)

			group.write_text(json.dumps({"items": ["mainmod:known"]}), encoding="utf-8")
			validate_hopper_filter_group_references(root)


if __name__ == "__main__":
	unittest.main()
