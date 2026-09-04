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

# Build and start only the production API and web containers.
"${compose[@]}" up --build --detach --wait api web

# Seed the migrated test database before Playwright sends requests.
"${compose[@]}" exec -T api node /tests/fixtures/seed-playwright-database.js

# Run all browser and HTTP checks against the isolated stack.
npm run test:playwright
