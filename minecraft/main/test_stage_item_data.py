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

	def test_related_groups_cover_their_families(self):
		root = Path(__file__).parent
		group_paths = (root / "data/data/hopper_filter_groups").glob("*.json")
		groups = {path.stem: set(json.loads(path.read_text(encoding="utf-8")).get("items", []))
			for path in group_paths}
		expected = {
			"resource_copper": set().union(*(items for name, items in groups.items()
				if name.startswith("copper_states_")), groups["gear_copper"],
				{"minecraft:copper_horse_armor", "minecraft:copper_nautilus_armor", "minecraft:copper_torch"}),
			"resource_blocks": {"minecraft:amethyst_block", "minecraft:coal_block", "minecraft:copper_block",
				"minecraft:lapis_block", "minecraft:quartz_block", "minecraft:redstone_block"},
			"lighting": groups["copper_states_copper_bulb"] |
				{"minecraft:beacon", "minecraft:campfire", "minecraft:end_rod",
				"minecraft:sea_pickle", "minecraft:soul_campfire"},
			"redstone": set().union(groups["copper_states_copper_bulb"],
				groups["copper_states_lightning_rod"], groups["buttons"],
				groups["pressure_plates"], groups["rails"],
				{"minecraft:crafter", "minecraft:dispenser", "minecraft:dropper",
				"minecraft:hopper", "minecraft:piston", "minecraft:sticky_piston"}),
			"mud": {"minecraft:packed_mud"} | {item for item in groups["bricks"] if ":mud_brick" in item},
			"stone_bricks": {item for item in groups["bricks"] if "stone_brick" in item
				and "end_stone_brick" not in item and "blackstone_brick" not in item},
			"wood_items": set().union(*(items for name, items in groups.items()
				if name.startswith("woodset_"))),
			"woodset_bamboo": {"minecraft:bamboo"},
			"woodset_crimson": {"minecraft:nether_wart_block"},
			"woodset_mangrove": {"minecraft:mangrove_propagule", "minecraft:mangrove_roots",
				"minecraft:muddy_mangrove_roots"},
			"gliding_gear": {"minecraft:elytra", "mainmod:charm-glider"},
			"soul": {"mainmod:soul", "mainmod:soul-shard"},
			"villager_workstations": {"minecraft:barrel", "minecraft:blast_furnace",
				"minecraft:brewing_stand", "minecraft:cartography_table", "minecraft:cauldron",
				"minecraft:composter", "minecraft:fletching_table", "minecraft:grindstone",
				"minecraft:lectern", "minecraft:loom", "minecraft:smithing_table",
				"minecraft:smoker", "minecraft:stonecutter"},
		}
		for name, items in expected.items():
			with self.subTest(group=name):
				self.assertFalse(items - groups[name], f"{name} is missing {sorted(items - groups[name])}")
		self.assertEqual(groups["villager_workstations"], expected["villager_workstations"])
		validate_hopper_filter_group_references(root)


if __name__ == "__main__":
	unittest.main()
