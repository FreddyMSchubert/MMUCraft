from __future__ import annotations

import argparse
import filecmp
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


TOGGLE_ID = re.compile(r"^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$")
FAKE_RECIPE_TYPES = {
	"mainmod:fake_crafting_shaped",
	"mainmod:fake_crafting_shapeless",
}
SOURCE_DATA_ROOT = Path("data/data")
STAGED_DATA_ROOT = Path("mod/src/main/resources/data/mainmod/dont_edit_auto_generated")


def validate_gameplay_toggle_references(root: Path) -> None:
	drops_path = root / "data" / "drops.json"
	drops = json.loads(drops_path.read_text(encoding="utf-8"))
	if (
		not isinstance(drops, list)
		or not all(isinstance(drop, dict) and isinstance(drop.get("id"), str)
			and TOGGLE_ID.fullmatch(drop["id"]) and isinstance(drop.get("name"), str)
			and isinstance(drop.get("date"), str) and isinstance(drop.get("description"), str)
			for drop in drops)
		or len({drop["id"] for drop in drops}) != len(drops)
	):
		raise ValueError(f"Invalid drop catalog: {drops_path}")
	drop_ids = {drop["id"] for drop in drops}
	known = drop_ids
	migrations = "\n".join(
		path.read_text(encoding="utf-8")
		for path in sorted((root.parents[1] / "services" / "api" / "drizzle").glob("*.sql"))
	)
	for toggle in known - {"unthemed"}:
		if f"('{toggle}'," not in migrations:
			raise ValueError(f"Gameplay toggle is not seeded by an API migration: {toggle}")

	for path in sorted((root / "data" / "data" / "items").rglob("item.json")):
		item = json.loads(path.read_text(encoding="utf-8"))
		if "drop" in item:
			raise ValueError(f"Root item drop is obsolete: {path}")
		shop = item.get("shopPurchasable")
		if isinstance(shop, dict):
			_validate_toggle(shop.get("gameplayToggle"), known, path)
		deco = item.get("decoBlock")
		if isinstance(deco, dict):
			_validate_toggle(deco.get("gameplayToggle"), known, path)
		for recipe in item.get("craftable", {}).get("recipes", []):
			_validate_toggle(recipe.get("gameplayToggle"), known, path)

	for path in sorted((root / "data" / "data" / "dailies" / "catalog").rglob("*.daily.json")):
		daily = json.loads(path.read_text(encoding="utf-8"))
		_validate_toggle(daily.get("drop"), known, path)

	for path in sorted((root / "mod" / "src" / "main" / "resources" / "data" / "mainmod" / "recipe").rglob("*.json")):
		recipe = json.loads(path.read_text(encoding="utf-8"))
		if recipe.get("type") in FAKE_RECIPE_TYPES:
			_validate_toggle(recipe.get("gameplayToggle"), known, path)

	knowledge_root = root.parents[1] / "services" / "web" / "public" / "knowledge"
	for path in sorted(knowledge_root.rglob("*.md")):
		metadata = re.match(r"^====\r?\n([\s\S]*?)\r?\n====", path.read_text(encoding="utf-8"))
		if not metadata:
			continue
		match = re.search(r"^gameplayToggle:\s*(.*?)\s*$", metadata.group(1), re.MULTILINE)
		_validate_toggle(match.group(1) if match else None, known, path)


def _validate_toggle(toggle: object, known: set[str], path: Path) -> None:
	if toggle is None or toggle == "":
		return
	if not isinstance(toggle, str) or toggle not in known:
		raise ValueError(f"Unknown gameplay toggle {toggle!r}: {path}")


def remove_empty_parents(path: Path, stop: Path) -> None:
	while path != stop and path.exists() and not any(path.iterdir()):
		path.rmdir()
		path = path.parent


def sync_tree(source: Path, destination: Path) -> tuple[int, int]:
	destination.mkdir(parents=True, exist_ok=True)
	source_files = {
		path.relative_to(source)
		for path in source.rglob("*")
		if path.is_file()
	}
	destination_files = {
		path.relative_to(destination)
		for path in destination.rglob("*")
		if path.is_file()
	}

	copied = 0
	removed = 0

	for rel in sorted(destination_files - source_files):
		target = destination / rel
		target.unlink()
		remove_empty_parents(target.parent, destination)
		removed += 1

	for rel in sorted(source_files):
		src = source / rel
		dst = destination / rel
		if dst.exists() and filecmp.cmp(src, dst, shallow=False):
			continue

		dst.parent.mkdir(parents=True, exist_ok=True)
		shutil.copy2(src, dst)
		copied += 1

	return copied, removed


def stage_data(root: Path, *, validate_references: bool = True) -> tuple[Path, Path, int, int]:
	source = root / SOURCE_DATA_ROOT
	destination = root / STAGED_DATA_ROOT

	if not source.is_dir():
		raise FileNotFoundError(f"Source data directory does not exist: {source}")

	if validate_references:
		validate_gameplay_toggle_references(root)
	copied = 0
	removed = 0
	for source_directory in sorted(path for path in source.iterdir() if path.is_dir()):
		directory_copied, directory_removed = sync_tree(
			source_directory,
			destination / source_directory.name,
		)
		copied += directory_copied
		removed += directory_removed
		legacy = root / STAGED_DATA_ROOT.parent / source_directory.name
		if legacy.exists():
			if not legacy.resolve().is_relative_to((root / STAGED_DATA_ROOT.parent).resolve()):
				raise ValueError(f"Unsafe legacy staged path: {legacy}")
			shutil.rmtree(legacy)

	subprocess.run([
		sys.executable,
		str(root / "respack" / "items-respack-generator" / "generate_recipes.py"),
		"--source", str(source / "items"),
		"--output", str(root / "mod" / "src" / "main" / "resources" / "data" / "mainmod" / "recipe" / "dont_edit_auto_generated"),
	], check=True)
	return source, destination, copied, removed


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--root", required=True)
	args = parser.parse_args()

	root = Path(args.root).resolve()
	try:
		source, destination, copied, removed = stage_data(root)
	except FileNotFoundError as exc:
		raise SystemExit(str(exc)) from exc
	print(
		f"Staged data: {source} -> {destination} "
		f"({copied} copied, {removed} removed)"
	)


if __name__ == "__main__":
	main()
