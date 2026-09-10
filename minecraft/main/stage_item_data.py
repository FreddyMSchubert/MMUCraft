from __future__ import annotations

import argparse
import filecmp
import json
import re
import shutil
from pathlib import Path


TOGGLE_ID = re.compile(r"^[a-z0-9._-]+(?:/[a-z0-9._-]+)*$")
FAKE_RECIPE_TYPES = {
	"mainmod:fake_crafting_shaped",
	"mainmod:fake_crafting_shapeless",
}
SOURCE_DATA_ROOT = Path("data/data")
STAGED_DATA_ROOT = Path("mod/src/main/resources/data/mainmod")


def validate_gameplay_toggle_references(root: Path) -> None:
	catalog_path = root / "data" / "gameplay-toggles.json"
	toggles = json.loads(catalog_path.read_text(encoding="utf-8"))
	if (
		not isinstance(toggles, list)
		or not all(isinstance(toggle, str) and TOGGLE_ID.fullmatch(toggle) for toggle in toggles)
		or len(toggles) != len(set(toggles))
	):
		raise ValueError(f"Invalid gameplay toggle catalog: {catalog_path}")
	known = set(toggles)
	migrations = "\n".join(
		path.read_text(encoding="utf-8")
		for path in sorted((root.parents[1] / "services" / "api" / "drizzle").glob("*.sql"))
	)
	for toggle in toggles:
		if f"('{toggle}'," not in migrations:
			raise ValueError(f"Gameplay toggle is not seeded by an API migration: {toggle}")

	for path in sorted((root / "data" / "data" / "items").rglob("item.json")):
		item = json.loads(path.read_text(encoding="utf-8"))
		shop = item.get("shopPurchasable")
		if isinstance(shop, dict):
			_validate_toggle(shop.get("gameplayToggle"), known, path)

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
	if toggle is None:
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
