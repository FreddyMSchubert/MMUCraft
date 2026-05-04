#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REF="${1:?expected docker ref}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MOD_DIR="${SCRIPT_DIR}/mod"
PROTO_DIR="${REPO_ROOT}/proto"
AUTH_PROTO="${PROTO_DIR}/auth.proto"

if [[ ! -f "${AUTH_PROTO}" ]]; then
    echo "Missing shared protobuf contract: ${AUTH_PROTO}" >&2
    exit 1
fi

echo '==> Validating and staging item data'
python3 "${SCRIPT_DIR}/stage_item_data.py" --root "${SCRIPT_DIR}"

echo '==> Building Fabric mod'
pushd "${MOD_DIR}" >/dev/null
./gradlew generateProto
./gradlew runDatagen
./gradlew build
popd >/dev/null

echo '==> Building merged resource pack'
python3 "${SCRIPT_DIR}/respack/build-main-pack.py"

echo "==> Building minecraft image: ${EXPECTED_REF}"
docker build -t "${EXPECTED_REF}" -f "${SCRIPT_DIR}/Dockerfile" "${SCRIPT_DIR}"