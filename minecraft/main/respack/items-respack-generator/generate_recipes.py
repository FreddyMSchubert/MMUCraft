"""Generate mod recipe JSON from item.json craftable definitions."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


RECIPE_ID = re.compile(r"^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$")
FAKE_TYPES = {"mainmod:fake_crafting_shaped", "mainmod:fake_crafting_shapeless"}


def generate(source: Path, output: Path) -> int:
	if output.name != "dont_edit_auto_generated" or output.parent.name != "recipe" or output.parent.parent.name != "mainmod":
		raise ValueError(f"Recipe output must be data/mainmod/recipe/dont_edit_auto_generated: {output}")
	generated: dict[str, dict] = {}
	for item_path in sorted(source.rglob("item.json")):
		item = json.loads(item_path.read_text(encoding="utf-8"))
		item_id = item["id"]
		for recipe in item.get("craftable", {}).get("recipes", []):
			recipe_id = recipe["id"]
			if not RECIPE_ID.fullmatch(recipe_id):
				raise ValueError(f"Invalid recipe id {recipe_id!r}: {item_path}")
			if recipe_id in generated:
				raise ValueError(f"Duplicate generated recipe id {recipe_id!r}: {item_path}")
			if recipe["type"] == "mainmod:fake_smelting":
				if recipe["result"] != f"mainmod:{item_id}":
					raise ValueError(f"Recipe {recipe_id} does not produce {item_id}: {item_path}")
			elif recipe["type"] in FAKE_TYPES:
				if recipe["result"]["stack"] != f"mainmod:{item_id}":
					raise ValueError(f"Recipe {recipe_id} does not produce {item_id}: {item_path}")
			else:
				if recipe["result"]["id"] != f"mainmod:{item_id}":
					raise ValueError(f"Recipe {recipe_id} does not produce {item_id}: {item_path}")
			generated[recipe_id] = {key: value for key, value in recipe.items() if key != "id"}

	output.mkdir(parents=True, exist_ok=True)
	for old in output.rglob("*.json"):
		if not old.resolve().is_relative_to(output.resolve()):
			raise ValueError(f"Unsafe generated recipe path: {old}")
		old.unlink()
	for directory in sorted((p for p in output.rglob("*") if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
		if not any(directory.iterdir()):
			directory.rmdir()
	for recipe_id, recipe in generated.items():
		path = output / f"{recipe_id}.json"
		path.parent.mkdir(parents=True, exist_ok=True)
		path.write_text(json.dumps(recipe, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
	return len(generated)


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--source", type=Path, required=True)
	parser.add_argument("--output", type=Path, required=True)
	args = parser.parse_args()
	count = generate(args.source.resolve(), args.output.resolve())
	print(f"Generated {count} item recipes in {args.output}")


if __name__ == "__main__":
	main()
