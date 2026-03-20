from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--root", required=True)
	args = parser.parse_args()

	root = Path(args.root).resolve()
	source = root / "data" / "data" / "items"
	destination = root / "mod" / "src" / "main" / "resources" / "data" / "mainmod" / "items"

	if not source.is_dir():
		raise SystemExit(f"Source items directory does not exist: {source}")

	if destination.exists():
		shutil.rmtree(destination)

	shutil.copytree(source, destination)
	print(f"Staged item data: {source} -> {destination}")


if __name__ == "__main__":
	main()