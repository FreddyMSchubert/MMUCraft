#!/usr/bin/env bash
set -Eeuo pipefail

compose=(docker compose -f tests/compose.yaml)

# Remove the test database and network on success, failure, or interruption.
cleanup() {
  status=$?
  if (( status != 0 )); then
    "${compose[@]}" ps --all || true
    "${compose[@]}" logs --no-color api web || true
  fi
  "${compose[@]}" down --volumes --remove-orphans
}
trap cleanup EXIT

# CI tests the images that will receive the release tag. Local runs build them here.
if [[ -n ${PLAYWRIGHT_API_IMAGE:-} || -n ${PLAYWRIGHT_WEB_IMAGE:-} ]]; then
  : "${PLAYWRIGHT_API_IMAGE:?set both test image names}"
  : "${PLAYWRIGHT_WEB_IMAGE:?set both test image names}"
  "${compose[@]}" up --no-build --pull always --detach --wait api web
else
  "${compose[@]}" up --build --detach --wait api web
fi

# Seed the migrated test database before Playwright sends requests.
"${compose[@]}" exec -T api node /tests/fixtures/seed-playwright-database.js

# Run all browser and HTTP checks against the isolated stack.
npm run test:playwright
