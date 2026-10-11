"""Find reusable GHCR images from their actual Docker build inputs."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
INPUTS = {
    "api": [
        ".dockerignore",
        "services/api/Dockerfile",
        "services/api/package.json",
        "services/api/package-lock.json",
        "services/api/tsconfig.json",
        "services/api/drizzle.config.ts",
        "services/api/src",
        "services/api/drizzle",
        "services/web/public/knowledge",
        "minecraft/main/data/data/items",
        "minecraft/main/data/drops.json",
        "minecraft/main/respack/packs/general-pack/assets/general-pack/textures/item/enderite",
        "minecraft/main/respack/packs/general-pack/assets/minecraft/textures/block/alien_debris.png",
        "minecraft/main/respack/packs/general-pack/assets/minecraft/textures/block/enderite_block.png",
        "minecraft/main/respack/packs/general-pack/assets/general-pack/textures/item/wallet/wallet-0.png",
        "proto",
    ],
    "web": [
        "services/web/.dockerignore",
        "services/web/Dockerfile",
        "services/web/package.json",
        "services/web/package-lock.json",
        "services/web/tsconfig.json",
        "services/web/next.config.ts",
        "services/web/public",
        "services/web/src",
    ],
    "mc": [
        "minecraft/main/.dockerignore",
        "minecraft/main/Dockerfile",
        "minecraft/main/mod/build/libs",
        "minecraft/main/config",
        "minecraft/main/server.properties.generated",
        "minecraft/main/server-icon.jpg",
    ],
    "velocity": [
        "services/velocity/.dockerignore",
        "services/velocity/Dockerfile",
        "services/velocity/config",
        "services/velocity/plugin/build/libs",
    ],
    "waiting-room": [
        ".dockerignore",
        "minecraft/waiting-room/Dockerfile",
        "minecraft/waiting-room/plugin/build/libs",
        "minecraft/waiting-room/server.properties",
        "minecraft/waiting-room/bukkit.yml",
        "minecraft/waiting-room/paper-global.yml",
        "minecraft/waiting-room/prepare-and-start.sh",
    ],
    "event": [
        ".dockerignore",
        "minecraft/surprising-saturday/Dockerfile",
        "minecraft/surprising-saturday/mods",
        "minecraft/surprising-saturday/chunklocked/build/libs",
        "minecraft/surprising-saturday/server.properties",
        "minecraft/surprising-saturday/prepare-and-start.sh",
    ],
    "event-controller": [
        "services/event-controller/Dockerfile",
        "services/event-controller/controller.mjs",
    ],
    "backup": [
        "services/backup/Dockerfile",
        "services/backup/backup-manager.sh",
        "services/backup/backup-excludes.txt",
    ],
}
DOCKERFILES = {name: next(path for path in paths if path.endswith("Dockerfile")) for name, paths in INPUTS.items()}


def base_images(dockerfile: Path):
    args = {}
    stages = set()
    for line in dockerfile.read_text(encoding="utf-8").splitlines():
        parts = line.strip().split()
        if not parts:
            continue
        if parts[0].upper() == "ARG" and "=" in parts[1]:
            name, value = parts[1].split("=", 1)
            args[name] = value
        elif parts[0].upper() == "FROM":
            image = re.sub(r"\$\{(\w+)\}", lambda match: args[match.group(1)], parts[1])
            if image not in stages:
                yield image
            if len(parts) >= 4 and parts[2].upper() == "AS":
                stages.add(parts[3])


def fingerprint(name: str, manifests: dict[str, bytes]) -> str:
    digest = hashlib.sha256()
    digest.update(b"image-inputs-v1\0linux/amd64\0")
    digest.update(Path(__file__).read_bytes())
    for relative in INPUTS[name]:
        path = ROOT / relative
        if not path.exists():
            raise FileNotFoundError(path)
        files = sorted(path.rglob("*")) if path.is_dir() else [path]
        for file in files:
            if not file.is_file():
                continue
            digest.update(file.relative_to(ROOT).as_posix().encode() + b"\0")
            if file.is_symlink():
                digest.update(os.readlink(file).encode() + b"\0")
            with file.open("rb") as source:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    digest.update(chunk)
            digest.update(b"\0")
    for image in sorted(set(base_images(ROOT / DOCKERFILES[name]))):
        if image not in manifests:
            raw = subprocess.check_output(["docker", "buildx", "imagetools", "inspect", "--raw", image])
            manifests[image] = json.dumps(json.loads(raw), sort_keys=True, separators=(",", ":")).encode()
        digest.update(image.encode() + b"\0" + hashlib.sha256(manifests[image]).digest())
    if name == "web":
        for variable in ("SITE_RELEASE_NAME", "SITE_RELEASE_URL"):
            digest.update(variable.encode() + b"\0" + os.environ.get(variable, "").encode() + b"\0")
    return digest.hexdigest()


def main() -> None:
    prefix = os.environ["IMAGE_PREFIX"]
    output = Path(os.environ["GITHUB_OUTPUT"])
    manifests = {}
    with output.open("a", encoding="utf-8") as stream:
        for name in sys.argv[1:]:
            tag = f"input-{fingerprint(name, manifests)}"
            image = f"{prefix}-{name}:{tag}"
            exists = subprocess.run(
                ["docker", "buildx", "imagetools", "inspect", image],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            ).returncode == 0
            key = name.replace("-", "_")
            stream.write(f"{key}_image={image}\n{key}_exists={str(exists).lower()}\n")
            print(f"{name}: {'reuse' if exists else 'build'} {image}")


if __name__ == "__main__":
    main()
