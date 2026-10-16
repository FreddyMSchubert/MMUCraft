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
			"gliding_gear": {"minecraft:elytra", "mainmod:charm-glider", "mainmod:charm-wings"},
			"soul": {"mainmod:soul", "mainmod:soul-shard"},
			"sniffer_flowers": {"minecraft:pitcher_plant", "minecraft:pitcher_pod",
				"minecraft:torchflower", "minecraft:torchflower_seeds"},
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
		self.assertEqual(groups["sniffer_flowers"], expected["sniffer_flowers"])
		validate_hopper_filter_group_references(root)

	def test_catalog_features_match_public_groups(self):
		root = Path(__file__).parent / "data/data"
		items = {}
		for path in (root / "items").rglob("item.json"):
			item = json.loads(path.read_text(encoding="utf-8"))
			items["mainmod:" + item["id"]] = (item, path)
		groups = {path.stem: json.loads(path.read_text(encoding="utf-8"))
			for path in (root / "hopper_filter_groups").glob("*.json")}
		cosmetics = {item_id: (item, path) for item_id, (item, path) in items.items()
			if item["modelType"] == "cosmetic"}

		def emits_light(value):
			if isinstance(value, dict):
				return any((key == "light_emission" and isinstance(child, (int, float)) and child > 0)
					or emits_light(child) for key, child in value.items())
			if isinstance(value, list):
				return any(emits_light(child) for child in value)
			return False

		expected = {
			"charms": {item_id for item_id, (item, _) in items.items() if "equippableCharm" in item},
			"cosmetics": set(cosmetics),
			"decoblocks": {item_id for item_id, (item, _) in items.items() if "decoBlock" in item},
			"dyeable_cosmetics": {item_id for item_id, (item, _) in cosmetics.items()
				if "dyeable" in item},
			"animated_cosmetics": {item_id for item_id, (_, path) in cosmetics.items()
				if any(path.parent.glob("*.png.mcmeta"))},
			"emissive_cosmetics": {item_id for item_id, (_, path) in cosmetics.items()
				if emits_light(json.loads(path.with_name("model.json").read_text(encoding="utf-8")))},
			"particle_cosmetics": {item_id for item_id, (item, _) in cosmetics.items()
				if "particleEmission" in item},
			"placeable_cosmetics": {item_id for item_id, (item, _) in cosmetics.items()
				if "decoBlock" in item},
		}
		families = {
			"cosmetic_animal_headwear": "_animals",
			"cosmetic_head_accessories": "_antlers",
			"cosmetic_crowns": "_crowns",
			"cosmetic_eyewear": "_glasses",
			"cosmetic_hoods": "_hoods",
			"cosmetic_facewear": "_masks",
			"cosmetic_novelty_headwear": "_misc",
			"cosmetic_moustaches": "_moustaches",
			"cosmetic_teletubbies": "_teletubbies",
			"cosmetic_general_headwear": "_true-hats",
			"cosmetic_villager": "_villager",
			"cosmetic_pet_hats": "_animals/_pets",
			"cosmetic_biome_hats": "_misc/_biomes",
		}
		for name, folder in families.items():
			family_root = root / "items/cosmetics" / folder
			expected[name] = {item_id for item_id, (_, path) in cosmetics.items()
				if path.is_relative_to(family_root)}
		for name, item_ids in expected.items():
			with self.subTest(group=name):
				self.assertEqual(set(groups[name]["items"]), item_ids)
		self.assertEqual([name for name, group in groups.items()
			if "charm" in group["name"].lower()], ["charms"])


if __name__ == "__main__":
	unittest.main()
