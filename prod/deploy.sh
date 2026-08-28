#!/bin/sh
set -eu

tag=${1:-}
image_prefix=${2:-}
warning_minutes=${3:-3}
force=${4:-false}

case "$tag" in
	''|*[!A-Za-z0-9_.-]*) echo "Invalid image tag: $tag" >&2; exit 2 ;;
esac
case "$image_prefix" in
	ghcr.io/*) ;;
	*) echo "Invalid GHCR image prefix: $image_prefix" >&2; exit 2 ;;
esac
case "$image_prefix" in
	*[!a-z0-9./_-]*) echo "Invalid GHCR image prefix: $image_prefix" >&2; exit 2 ;;
esac
[ "${#tag}" -le 128 ] || { echo "Image tag is too long" >&2; exit 2; }
case "$warning_minutes" in
	0|[1-9]|[1-5][0-9]|60) ;;
	*) echo "Restart warning must be a whole number from 0 to 60 minutes" >&2; exit 2 ;;
esac
case "$force" in
	true|false) ;;
	*) echo "Force must be true or false" >&2; exit 2 ;;
esac

if [ ! -f .env ]; then
	echo "Missing $(pwd)/.env; copy .env.example and fill it first." >&2
	exit 2
fi

set -a
# .env is intentionally shell-compatible; see .env.example.
. ./.env
set +a
: "${PUBLIC_URL:?set PUBLIC_URL in .env}"
: "${AUTH_CODE_SECRET:?set AUTH_CODE_SECRET in .env}"
: "${RESEND_API_KEY:?set RESEND_API_KEY in .env}"
: "${RESEND_FROM:?set RESEND_FROM in .env}"
: "${GRAFANA_ADMIN_PASSWORD:?set GRAFANA_ADMIN_PASSWORD in .env}"
: "${VELOCITY_API_SECRET:?set VELOCITY_API_SECRET in .env}"
: "${VELOCITY_FORWARDING_SECRET:?set VELOCITY_FORWARDING_SECRET in .env}"
case "$PUBLIC_URL" in
	https://*) ;;
	*) echo "PUBLIC_URL must use HTTPS" >&2; exit 2 ;;
esac
public_host=${PUBLIC_URL#https://}
case "$public_host" in
	''|*[!A-Za-z0-9.-]*|.*|*..*|*.) echo "PUBLIC_URL must not contain a port, path, query, or fragment" >&2; exit 2 ;;
esac
[ "${#AUTH_CODE_SECRET}" -ge 32 ] || { echo "AUTH_CODE_SECRET must be at least 32 characters" >&2; exit 2; }
[ "${#GRAFANA_ADMIN_PASSWORD}" -ge 24 ] || { echo "GRAFANA_ADMIN_PASSWORD must be at least 24 characters" >&2; exit 2; }
[ "${#VELOCITY_API_SECRET}" -ge 32 ] || { echo "VELOCITY_API_SECRET must be at least 32 characters" >&2; exit 2; }
[ "${#VELOCITY_FORWARDING_SECRET}" -ge 32 ] || { echo "VELOCITY_FORWARDING_SECRET must be at least 32 characters" >&2; exit 2; }
case "$AUTH_CODE_SECRET:$VELOCITY_API_SECRET:$VELOCITY_FORWARDING_SECRET:$RESEND_API_KEY" in
	*replace*) echo "Replace the placeholder secrets in .env" >&2; exit 2 ;;
esac
case "$GRAFANA_ADMIN_PASSWORD" in
	*replace*) echo "Replace the placeholder Grafana password in .env" >&2; exit 2 ;;
esac

umask 077
printf 'IMAGE_PREFIX=%s\nIMAGE_TAG=%s\nPUBLIC_HOST=%s\nMONITORING_CONFIG_PATH=./monitoring\n' "$image_prefix" "$tag" "$public_host" > .release.env
mkdir -p data/api data/minecraft data/velocity
[ -e data/api/signup-allowlist.txt ] || : > data/api/signup-allowlist.txt
printf '%s\n' "$VELOCITY_FORWARDING_SECRET" > data/velocity/forwarding.secret
chmod 775 data/api data/minecraft data/velocity
chmod 664 data/api/signup-allowlist.txt
chmod 600 data/velocity/forwarding.secret

dc() {
	docker compose --env-file .env --env-file .release.env "$@"
}

set_property() {
	file=$1
	key=$2
	value=$3
	tmp="${file}.tmp.$$"
	awk -v key="$key" -v value="$value" '
		BEGIN { found = 0 }
		index($0, key "=") == 1 { print key "=" value; found = 1; next }
		{ print }
		END { if (!found) print key "=" value }
	' "$file" > "$tmp"
	mv "$tmp" "$file"
}

dc config --quiet
dc pull --quiet
dc run --rm --no-deps alloy validate /etc/alloy/config.alloy

api_image="${image_prefix}-api:${tag}"
mc_image="${image_prefix}-mc:${tag}"
velocity_image="${image_prefix}-velocity:${tag}"
defaults=$(mktemp)
trap 'rm -f "$defaults"' EXIT HUP INT TERM
docker run --rm --entrypoint cat "$mc_image" /defaults/server.properties > "$defaults"
docker run --rm --entrypoint cat "$velocity_image" /config/velocity.toml > data/velocity/velocity.toml
chmod 600 data/velocity/velocity.toml

server_properties=data/minecraft/server.properties
cp "$defaults" "$server_properties"

set_property "$server_properties" resource-pack "${PUBLIC_URL%/}/packs/main.zip"
chmod 664 "$server_properties"

if [ "$warning_minutes" -gt 0 ] && dc ps --status running --services | grep -qx minecraft; then
	dc exec -T --user "${PUID:-1000}:${PGID:-1000}" minecraft mc-send-to-console \
		"say Server will be restarted in $warning_minutes minutes. This will not take long, roughly 3 minutes, 5 at max, otherwise something is wrong. Please be careful, don't go underwater etc. See you soon! :)"
	sleep "$((warning_minutes * 60))"
fi

if dc ps --status running --services | grep -qx minecraft; then
	status=0
	dc exec -T api node -e '
		fetch("http://127.0.0.1:8080/api/internal/shutdown", {
			method: "POST",
			signal: AbortSignal.timeout(30_000),
		}).then(async response => {
			if (response.status === 404) process.exit(42);
			if (!response.ok) throw new Error(response.status + " " + await response.text());
			console.log(await response.text());
		}).catch(error => { console.error(error); process.exit(1); });
	' || status=$?
	if [ "$status" -eq 42 ]; then
		echo "Current API does not support draining yet; using Minecraft's graceful stop for this deployment."
	elif [ "$status" -ne 0 ]; then
		if [ "$force" = true ]; then
			echo "WARNING: API drain/save failed; forcing deployment." >&2
		else
			echo "API drain/save failed; restarting the drained API before aborting." >&2
			dc restart api
			dc up -d --wait --wait-timeout 60 api
			exit "$status"
		fi
	fi
	dc stop minecraft
fi

legacy_bans=data/minecraft/banned-players.json
if [ -s "$legacy_bans" ]; then
	[ -e "${legacy_bans}.pre-velocity" ] || cp -p "$legacy_bans" "${legacy_bans}.pre-velocity"
	docker run --rm \
		--user "${PUID:-1000}:${PGID:-1000}" \
		--volume "$PWD/data/minecraft:/data" \
		--entrypoint node \
		"$api_image" \
		-e '
			const fs = require("node:fs");
			const path = "/data/banned-players.json";
			const entries = JSON.parse(fs.readFileSync(path, "utf8"));
			const kept = entries.filter(entry => entry.source !== "MMU Minecraft Society website");
			if (kept.length !== entries.length) {
				fs.writeFileSync(path + ".tmp", JSON.stringify(kept, null, 2) + "\n");
				fs.renameSync(path + ".tmp", path);
				console.log(`Removed ${entries.length - kept.length} legacy website ban(s).`);
			}
		'
fi

dc up -d --remove-orphans --force-recreate --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-600}"

# Never prune volumes: they contain the database and Minecraft world.
docker container prune -f >/dev/null
docker image prune -f --filter until=168h >/dev/null
docker builder prune -f --filter until=168h >/dev/null

dc ps
