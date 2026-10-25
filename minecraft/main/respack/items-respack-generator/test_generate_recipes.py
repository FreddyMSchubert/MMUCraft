import json
import tempfile
import unittest
from pathlib import Path

from generate_recipes import generate


class GenerateRecipesTest(unittest.TestCase):
	def test_shaped_recipe_keys_match_pattern(self):
		with tempfile.TemporaryDirectory() as directory:
			root = Path(directory)
			item_path = root / "items" / "gravestone" / "item.json"
			item_path.parent.mkdir(parents=True)
			output = root / "data" / "mainmod" / "recipe" / "dont_edit_auto_generated"
			recipe = {
				"id": "decoration/gravestone",
				"type": "mainmod:fake_crafting_shaped",
				"pattern": ["WW", "WW"],
				"key": {"S": "minecraft:tuff_brick_wall"},
				"result": {"stack": "mainmod:deco-gravestone"},
			}
			item = {"id": "deco-gravestone", "craftable": {"recipes": [recipe]}}
			item_path.write_text(json.dumps(item), encoding="utf-8")

			with self.assertRaisesRegex(ValueError, "missing keys \\['W'\\], unused keys \\['S'\\]"):
				generate(root / "items", output)

			recipe["key"] = {"W": "minecraft:tuff_brick_wall"}
			item_path.write_text(json.dumps(item), encoding="utf-8")
			self.assertEqual(generate(root / "items", output), 1)


if __name__ == "__main__":
	unittest.main()
