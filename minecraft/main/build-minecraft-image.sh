#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REF="${1:?expected docker ref}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="${SCRIPT_DIR}/mod"

echo '==> Validating and staging item data'
python "${SCRIPT_DIR}/build/stage_item_data.py" --root "${SCRIPT_DIR}"

echo '==> Building Fabric mod'
pushd "${MOD_DIR}" >/dev/null
./gradlew runDatagen
./gradlew build
popd >/dev/null

echo '==> Building merged resource pack'
python "${SCRIPT_DIR}/respack/build-main-pack.py"

echo "==> Building minecraft image: ${EXPECTED_REF}"
docker build -t "${EXPECTED_REF}" -f "${SCRIPT_DIR}/Dockerfile" "${SCRIPT_DIR}"