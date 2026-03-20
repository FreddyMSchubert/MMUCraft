from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ITEMS_ROOT = Path("data/items")
SCHEMA_ROOT = Path("data/validation/schemas/item")
ROOT_SCHEMA = SCHEMA_ROOT / "item.schema.json"
STAGED_ITEMS_ROOT = Path("mod/src/main/resources/data/mainmod/items")


class ItemDataError(RuntimeError):
	pass


def load_json(path: Path):
	try:
		return json.loads(path.read_text(encoding="utf-8"))
	except json.JSONDecodeError as exc:
		raise ItemDataError(f"Invalid JSON in {path}: {exc}") from exc


def discover_item_jsons(items_root: Path) -> list[tuple[Path, Path]]:
	if not items_root.exists():
		raise ItemDataError(f"Items root does not exist: {items_root}")

	found: list[tuple[Path, Path]] = []

	for directory in sorted(p for p in items_root.rglob("*") if p.is_dir()):
		child_dirs = any(child.is_dir() for child in directory.iterdir())
		item_json = directory / "item.json"

		if child_dirs:
			if item_json.exists():
				raise ItemDataError(
					f"{directory}: a folder cannot both contain subfolders and define an item"
				)
			continue

		if not item_json.exists():
			raise ItemDataError(
				f"{directory}: every leaf folder under data/items must contain item.json"
			)

		found.append((item_json, directory.relative_to(items_root)))

	if not found:
		raise ItemDataError(f"No item folders were found under {items_root}")

	return found


def build_schema_registry(schema_root: Path) -> Registry:
	registry = Registry()

	for path in sorted(schema_root.rglob("*.json")):
		schema = load_json(path)
		schema_id = schema.get("$id")
		if not isinstance(schema_id, str) or not schema_id:
			raise ItemDataError(f"{path}: every schema file must have a string $id")
		registry = registry.with_resource(schema_id, Resource.from_contents(schema))

	return registry


def format_error_path(error) -> str:
	path = "$"
	for part in error.absolute_path:
		path += f"[{part}]" if isinstance(part, int) else f".{part}"
	return path


def validate_item(schema_path: Path, registry: Registry, item_path: Path) -> None:
	schema = load_json(schema_path)
	item = load_json(item_path)
	validator = Draft202012Validator(schema, registry=registry)
	errors = sorted(validator.iter_errors(item), key=lambda e: list(e.absolute_path))

	if errors:
		raise ItemDataError(
			"\n".join(f"{item_path}: {format_error_path(e)}: {e.message}" for e in errors)
		)


def stage_items(staged_root: Path, item_jsons: list[tuple[Path, Path]]) -> None:
	if staged_root.exists():
		shutil.rmtree(staged_root)
	staged_root.mkdir(parents=True, exist_ok=True)

	for source, relative_dir in item_jsons:
		dest = staged_root / relative_dir / "item.json"
		dest.parent.mkdir(parents=True, exist_ok=True)
		shutil.copy2(source, dest)


def main() -> int:
	parser = argparse.ArgumentParser()
	parser.add_argument("--root", required=True, help="Path to minecraft/main")
	args = parser.parse_args()

	root = Path(args.root).resolve()
	items_root = root / ITEMS_ROOT
	schema_root = root / SCHEMA_ROOT
	root_schema = root / ROOT_SCHEMA
	staged_root = root / STAGED_ITEMS_ROOT

	if not root_schema.exists():
		raise ItemDataError(f"Root schema does not exist: {root_schema}")

	item_jsons = discover_item_jsons(items_root)
	registry = build_schema_registry(schema_root)

	for item_json, _ in item_jsons:
		validate_item(root_schema, registry, item_json)

	stage_items(staged_root, item_jsons)
	print(f"Validated and staged {len(item_jsons)} item definition(s).")
	return 0


if __name__ == "__main__":
	try:
		raise SystemExit(main())
	except ItemDataError as exc:
		print(f"ITEM DATA ERROR\n{exc}", file=sys.stderr)
		raise SystemExit(1)
