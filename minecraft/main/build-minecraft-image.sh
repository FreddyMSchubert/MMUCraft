#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REF="${1:?expected docker ref}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MOD_DIR="${SCRIPT_DIR}/mod"
PROTO_DIR="${REPO_ROOT}/proto"
GENERATED_SERVER_PROPERTIES="${SCRIPT_DIR}/server.properties.generated"

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

if [[ ! -f "${GENERATED_SERVER_PROPERTIES}" ]]; then
  echo "resource pack build did not create ${GENERATED_SERVER_PROPERTIES}" >&2
  exit 1
fi

shopt -s nullglob
jar_candidates=("${MOD_DIR}"/build/libs/*.jar)
shopt -u nullglob

jar_files=()
for jar in "${jar_candidates[@]}"; do
  if [[ "${jar}" != *-sources.jar ]]; then
    jar_files+=("${jar}")
  fi
done

if [[ "${#jar_files[@]}" -eq 0 ]]; then
  echo "Fabric mod build did not create a runtime jar in ${MOD_DIR}/build/libs" >&2
  exit 1
fi

DOCKER_CONTEXT="$(mktemp -d)"
cleanup() {
  rm -rf "${DOCKER_CONTEXT}"
}
trap cleanup EXIT

mkdir -p "${DOCKER_CONTEXT}/mod/build/libs"
cp "${SCRIPT_DIR}/Dockerfile" "${DOCKER_CONTEXT}/Dockerfile"
cp "${GENERATED_SERVER_PROPERTIES}" "${DOCKER_CONTEXT}/server.properties.generated"
cp "${jar_files[@]}" "${DOCKER_CONTEXT}/mod/build/libs/"

echo "==> Building minecraft image: ${EXPECTED_REF}"
docker build -t "${EXPECTED_REF}" -f "${DOCKER_CONTEXT}/Dockerfile" "${DOCKER_CONTEXT}"
