#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REF="${1:?expected image ref}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$SCRIPT_DIR/mod"

echo "==> Building Fabric mod"
(
    cd "$MOD_DIR"
    ./gradlew runDatagen
    ./gradlew build
)

echo "==> Building merged resource pack"
python3 "$SCRIPT_DIR/respack/build-main-pack.py"

echo "==> Building minecraft image: $EXPECTED_REF"
docker build -t "$EXPECTED_REF" -f "$SCRIPT_DIR/Dockerfile" "$SCRIPT_DIR"