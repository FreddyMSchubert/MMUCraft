#!/bin/sh
set -eu

tag=${1:-}
image_prefix=${2:-}

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
case "$PUBLIC_URL" in
	https://*) ;;
	*) echo "PUBLIC_URL must use HTTPS" >&2; exit 2 ;;
esac
public_host=${PUBLIC_URL#https://}
case "$public_host" in
	''|*[!A-Za-z0-9.-]*|.*|*..*|*.) echo "PUBLIC_URL must not contain a port, path, query, or fragment" >&2; exit 2 ;;
esac
[ "${#AUTH_CODE_SECRET}" -ge 32 ] || { echo "AUTH_CODE_SECRET must be at least 32 characters" >&2; exit 2; }
case "$AUTH_CODE_SECRET:$RESEND_API_KEY" in
	*replace*) echo "Replace the placeholder secrets in .env" >&2; exit 2 ;;
esac

umask 077
printf 'IMAGE_PREFIX=%s\nIMAGE_TAG=%s\nPUBLIC_HOST=%s\n' "$image_prefix" "$tag" "$public_host" > .release.env
mkdir -p data/api data/minecraft
chmod 775 data/api data/minecraft

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

mc_image="${image_prefix}-mc:${tag}"
defaults=$(mktemp)
trap 'rm -f "$defaults"' EXIT HUP INT TERM
docker run --rm --entrypoint cat "$mc_image" /defaults/server.properties > "$defaults"

server_properties=data/minecraft/server.properties
if [ ! -s "$server_properties" ]; then
	cp "$defaults" "$server_properties"
fi

for key in resource-pack-id resource-pack-sha1; do
	value=$(sed -n "s/^${key}=//p" "$defaults" | head -n 1)
	[ -n "$value" ] && set_property "$server_properties" "$key" "$value"
done
set_property "$server_properties" resource-pack "${PUBLIC_URL%/}/packs/main.zip"
chmod 664 "$server_properties"

dc run --rm --no-deps nginx -t
dc up -d --remove-orphans --force-recreate --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-600}"

# Never prune volumes: they contain the database and Minecraft world.
docker container prune -f >/dev/null
docker image prune -f >/dev/null
docker builder prune -f --filter until=168h >/dev/null

dc ps
