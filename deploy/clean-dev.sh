#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ $# != 2 || ! $1 =~ ^(0|-?[1-9][0-9]*)$ || ! $2 =~ ^(0|-?[1-9][0-9]*)$ || ${#1} -gt 9 || ${#2} -gt 9 ]] ||
	(( $1 < -29999984 || $1 > 29999984 || $2 < -29999984 || $2 > 29999984 )); then
	echo 'Usage: ./clean-dev.sh SPAWN_X SPAWN_Z (block coordinates)' >&2
	exit 2
fi
if [[ ! -f .release.env ]] || ! grep -qx 'COMPOSE_FILE=compose.yaml:compose.dev.yaml' .release.env; then
	echo 'Refusing cleanup: this directory is not configured for the deployed dev stack.' >&2
	exit 2
fi
if [[ ! -d data/minecraft/world || -L data/minecraft/world ]]; then
	echo 'Refusing cleanup: the main world is missing or is a symlink.' >&2
	exit 2
fi

properties=data/minecraft/server.properties
if [[ $(sed -n 's/^level-name=//p' "$properties") != world ]]; then
	echo 'Refusing cleanup: the configured world is not data/minecraft/world.' >&2
	exit 2
fi
view_distance=$(sed -n 's/^view-distance=//p' "$properties")
if [[ ! $view_distance =~ ^(0|[1-9][0-9]*)$ ]] || (( view_distance < 2 || view_distance > 32 )); then
	echo 'Refusing cleanup: view-distance must be between 2 and 32.' >&2
	exit 2
fi
for dir in data backups; do
	[[ -d $dir ]] || continue
	blocked=$(find "$dir" -type d ! -writable -print -quit) || {
		echo "Refusing cleanup: cannot inspect $dir permissions." >&2
		exit 2
	}
	if [[ -n $blocked ]]; then
		echo "Refusing cleanup: the deploy user cannot write to $blocked." >&2
		exit 2
	fi
done

floor_div() {
	local n=$1 d=$2
	if (( n < 0 )); then
		printf '%d\n' "$(( (n - d + 1) / d ))"
	else
		printf '%d\n' "$(( n / d ))"
	fi
}

min_x=$(( $(floor_div "$1" 16) - view_distance ))
max_x=$(( $(floor_div "$1" 16) + view_distance ))
min_z=$(( $(floor_div "$2" 16) - view_distance ))
max_z=$(( $(floor_div "$2" 16) + view_distance ))
min_rx=$(floor_div "$min_x" 32)
max_rx=$(floor_div "$max_x" 32)
min_rz=$(floor_div "$min_z" 32)
max_rz=$(floor_div "$max_z" 32)

printf 'Keeping chunk range X=%s..%s Z=%s..%s and every overlapping region.\n' "$min_x" "$max_x" "$min_z" "$max_z"
docker compose --env-file .env --env-file .release.env -f compose.yaml -f compose.dev.yaml down -v --remove-orphans

rm -rf backups data/chunklocked
rm -rf data/api/backup-staging data/api/logs data/api/cache data/velocity/logs
rm -rf data/minecraft/logs data/minecraft/crash-reports data/minecraft/downloads data/minecraft/cache data/minecraft/backups

while IFS= read -r -d '' file; do
	name=${file##*/}
	if [[ $name =~ ^r\.(-?[0-9]+)\.(-?[0-9]+)\.mca$ ]]; then
		x=${BASH_REMATCH[1]}
		z=${BASH_REMATCH[2]}
		if (( x < min_rx || x > max_rx || z < min_rz || z > max_rz )); then
			rm -- "$file"
		fi
	elif [[ $name =~ ^c\.(-?[0-9]+)\.(-?[0-9]+)\.mcc$ ]]; then
		x=$(floor_div "${BASH_REMATCH[1]}" 32)
		z=$(floor_div "${BASH_REMATCH[2]}" 32)
		if (( x < min_rx || x > max_rx || z < min_rz || z > max_rz )); then
			rm -- "$file"
		fi
	fi
done < <(find data/minecraft/world -type f \( -path '*/region/*' -o -path '*/entities/*' -o -path '*/poi/*' \) -print0)

docker image prune -af
docker builder prune -af
echo 'Dev storage cleaned. The stack is stopped; deploy it again when needed.'
