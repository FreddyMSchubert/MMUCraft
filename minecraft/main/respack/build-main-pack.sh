#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"

GENERATOR_DIR="$SCRIPT_DIR/items-respack-generator"
MERGER_DIR="$SCRIPT_DIR/ResourcePackMerger"
PACKS_DIR="$SCRIPT_DIR/packs"
ITEMS_DIR="$SCRIPT_DIR/../data/data/items"

GENERATED_DIR="$PACKS_DIR/generated"
MERGED_DIR="$PACKS_DIR/main-pack"
FINAL_ZIP="$PACKS_DIR/main-pack.zip"

WEB_PACKS_DIR="$REPO_ROOT/services/web/public/packs"
WEB_ZIP="$WEB_PACKS_DIR/main.zip"

echo "==> Generating resource pack from item definitions"
(
        cd "$GENERATOR_DIR"

        if [ ! -d node_modules ]; then
                npm ci
        fi

        rm -rf "$GENERATED_DIR"

        npm run generate -- \
                --source "$ITEMS_DIR" \
                --vanilla-armor ./vanilla_armor_assets \
                --output "$GENERATED_DIR"
)

echo "==> Building ResourcePackMerger"
if [ -x "$MERGER_DIR/mvnw" ]; then
        (
                cd "$MERGER_DIR"
                ./mvnw -q -DskipTests package
        )
else
        (
                cd "$MERGER_DIR"
                mvn -q -DskipTests package
        )
fi

MERGER_JAR="$(find "$MERGER_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort | head -n 1)"
if [ -z "$MERGER_JAR" ]; then
        echo "Could not find a built ResourcePackMerger jar in $MERGER_DIR/target" >&2
        exit 1
fi

rm -rf "$MERGED_DIR" "$FINAL_ZIP"
mkdir -p "$PACKS_DIR" "$WEB_PACKS_DIR"

MERGE_INPUTS=()
while IFS= read -r -d '' entry; do
        MERGE_INPUTS+=("$entry")
done < <(
        find "$PACKS_DIR" -mindepth 1 -maxdepth 1 \
                \( -type d -o \( -type f -name '*.zip' \) \) \
                ! -name 'main-pack' \
                ! -name 'main-pack.zip' \
                -print0 | sort -z
)

if [ "${#MERGE_INPUTS[@]}" -eq 0 ]; then
        echo "No input packs found in $PACKS_DIR" >&2
        exit 1
fi

echo "==> Merging packs"
printf ' - %s\n' "${MERGE_INPUTS[@]}"

java -jar "$MERGER_JAR" "${MERGE_INPUTS[@]}" "$MERGED_DIR"

echo "==> Creating zip archive"
jar --create --file "$FINAL_ZIP" --no-manifest -C "$MERGED_DIR" .

echo "==> Publishing zip for the website"
cp "$FINAL_ZIP" "$WEB_ZIP"

echo "Done:"
echo " - canonical archive: $FINAL_ZIP"
echo " - served archive:    $WEB_ZIP"