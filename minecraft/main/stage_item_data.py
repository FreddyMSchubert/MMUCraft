from __future__ import annotations

import argparse
import filecmp
import shutil
from pathlib import Path


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


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--root", required=True)
	args = parser.parse_args()

	root = Path(args.root).resolve()
	source = root / "data" / "data" / "items"
	destination = root / "mod" / "src" / "main" / "resources" / "data" / "mainmod" / "items"

	if not source.is_dir():
		raise SystemExit(f"Source items directory does not exist: {source}")

	copied, removed = sync_tree(source, destination)
	print(
		f"Staged item data: {source} -> {destination} "
		f"({copied} copied, {removed} removed)"
	)


if __name__ == "__main__":
	main()
