from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from stage_item_data import stage_data, validate_gameplay_toggle_references


ITEMS_ROOT = Path("data/data/items")
HOPPER_FILTER_GROUPS_ROOT = Path("data/data/hopper_filter_groups")
SCHEMA_ROOT = Path("data/validation/schemas/item")
ROOT_SCHEMA = SCHEMA_ROOT / "item.schema.json"
ITEM_ID = re.compile(r"^(?:minecraft|mainmod):[a-z0-9._-]+(?:/[a-z0-9._-]+)*$")


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
				f"{directory}: every leaf folder under data/data/items must contain item.json"
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


def validate_hopper_filter_groups(root: Path) -> None:
	groups_root = root / HOPPER_FILTER_GROUPS_ROOT
	for path in sorted(groups_root.glob("**/*.json")):
		group = load_json(path)
		allowed_keys = {"name", "items", "potions", "enchantments"}
		if not isinstance(group, dict) or "name" not in group or not set(group) <= allowed_keys:
			raise ItemDataError(f"{path}: expected 'name' and optional membership arrays")
		if not isinstance(group["name"], str) or not group["name"].strip():
			raise ItemDataError(f"{path}: name must be a non-empty string")
		memberships = [group.get(key, []) for key in ("items", "potions", "enchantments")]
		if not any(memberships):
			raise ItemDataError(f"{path}: a group must contain items, potions, or enchantments")
		for key, values in zip(("items", "potions", "enchantments"), memberships):
			if not isinstance(values, list) or not all(
				isinstance(value, str) and ITEM_ID.fullmatch(value) for value in values
			):
				raise ItemDataError(f"{path}: membership values must use minecraft: or mainmod: IDs")
			if key != "items" and any(not value.startswith("minecraft:") for value in values):
				raise ItemDataError(f"{path}: {key} values must use minecraft: IDs")
			if len(values) != len(set(values)):
				raise ItemDataError(f"{path}: duplicate membership ID")


def main() -> int:
	parser = argparse.ArgumentParser()
	parser.add_argument("--root", required=True, help="Path to minecraft/main")
	args = parser.parse_args()

	root = Path(args.root).resolve()
	items_root = root / ITEMS_ROOT
	schema_root = root / SCHEMA_ROOT
	root_schema = root / ROOT_SCHEMA

	if not root_schema.exists():
		raise ItemDataError(f"Root schema does not exist: {root_schema}")

	item_jsons = discover_item_jsons(items_root)
	registry = build_schema_registry(schema_root)
	try:
		validate_gameplay_toggle_references(root)
	except (OSError, ValueError, json.JSONDecodeError) as exc:
		raise ItemDataError(str(exc)) from exc

	for item_json, _ in item_jsons:
		validate_item(root_schema, registry, item_json)
	validate_hopper_filter_groups(root)

	_, _, copied, removed = stage_data(root, validate_references=False)
	print(
		f"Validated {len(item_jsons)} item definition(s) and staged data "
		f"({copied} copied, {removed} removed)."
	)
	return 0


if __name__ == "__main__":
	try:
		raise SystemExit(main())
	except ItemDataError as exc:
		print(f"ITEM DATA ERROR\n{exc}", file=sys.stderr)
		raise SystemExit(1)
