#!/bin/sh
set -eu

tag=${1:-}
image_prefix=${2:-}
warning_minutes=${3:-3}
force=${4:-false}
notify_update_complete=false
update_started=false
shutdown_attempted=false
defaults=''

# Validate deployment inputs before changing server state.
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

# Prepare release configuration and persistent data.
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

api_post() {
	dc exec -T api node -e '
		fetch("http://127.0.0.1:8080" + process.argv[1], {
			method: "POST",
			signal: AbortSignal.timeout(30_000),
		}).then(async response => {
			if (response.status === 404) process.exit(42);
			if (!response.ok) throw new Error(response.status + " " + await response.text());
			console.log(await response.text());
		}).catch(error => { console.error(error); process.exit(1); });
	' "$1"
}

graceful_failure() {
	if [ "$force" = true ]; then
		echo "WARNING: $1; forcing deployment after the failed attempt." >&2
	else
		echo "$1; aborting deployment." >&2
		exit 1
	fi
}

clear_update() {
	printf 'updating=false\n' > data/velocity/deployment.properties.tmp
	chmod 644 data/velocity/deployment.properties.tmp
	mv data/velocity/deployment.properties.tmp data/velocity/deployment.properties
	update_started=false
}

cancel_update() {
	clear_update
	if [ "$notify_update_complete" = true ]; then
		api_post /api/internal/deployment/cancel >/dev/null \
			|| echo "Could not send the update cancellation notice." >&2
	fi
}

cleanup() {
	status=$?
	trap - EXIT
	[ -z "$defaults" ] || rm -f "$defaults"
	if [ "$update_started" = true ]; then
		if [ "$shutdown_attempted" = false ]; then
			cancel_update
			echo "Deployment stopped before shutdown. The current server can accept players." >&2
		else
			echo "Deployment did not finish. Update status stays active until a successful deployment." >&2
		fi
	fi
	exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_proxy() {
	deadline=$(( $(date +%s) + 30 ))
	while [ "$(date +%s)" -lt "$deadline" ]; do
		ack=$(cat data/velocity/deployment-drained 2>/dev/null || true)
		case "$ack" in
			"$deployment_id true true"|"$deployment_id false true") return 0 ;;
			"$deployment_id true false"|"$deployment_id false false")
				[ "$1" = drained ] && return 0 ;;
		esac
		sleep 1
	done
	return 1
}

announce_update_complete() {
	[ "$notify_update_complete" = true ] || return 0
	api_post /api/internal/deployment/complete >/dev/null
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

# Pull and validate images before player downtime starts.
dc config --quiet
dc pull --quiet
dc run --rm --no-deps alloy validate /etc/alloy/config.alloy

api_image="${image_prefix}-api:${tag}"
mc_image="${image_prefix}-mc:${tag}"
velocity_image="${image_prefix}-velocity:${tag}"
defaults=$(mktemp)
docker run --rm --entrypoint cat "$mc_image" /defaults/server.properties > "$defaults"
docker run --rm --entrypoint cat "$velocity_image" /config/velocity.toml > data/velocity/velocity.toml
chmod 600 data/velocity/velocity.toml

server_properties=data/minecraft/server.properties
cp "$defaults" "$server_properties"

set_property "$server_properties" resource-pack "${PUBLIC_URL%/}/packs/main.zip"
chmod 664 "$server_properties"

# Warn players before the update starts.
running_services=$(dc ps --status running --services)
if printf '%s\n' "$running_services" | grep -qx minecraft; then
	if [ "$warning_minutes" -gt 0 ]; then
		dc exec -T --user "${PUID:-1000}:${PGID:-1000}" minecraft mc-send-to-console \
			"execute if entity @a run say Server update in $warning_minutes minutes. Please move to a safe place. Allow about 200-300 seconds for the update, then join again." \
			|| graceful_failure "Could not send the restart warning"
		sleep "$((warning_minutes * 60))"
	fi
	dc exec -T --user "${PUID:-1000}:${PGID:-1000}" minecraft mc-send-to-console \
		"execute if entity @a run say Server update starting now. Please join again in about 200-300 seconds. If it takes more than 10 minutes, contact the committee." \
		|| graceful_failure "Could not send the update message"
fi

# Velocity blocks new joins and confirms that all connections have closed.
if grep -qx 'updating=true' data/velocity/deployment.properties 2>/dev/null; then
	shutdown_attempted=true
fi
deployment_id="$(date +%s)-$$"
printf 'updating=true\nstartedAt=%s\nid=%s\n' "$(date +%s)" "$deployment_id" > data/velocity/deployment.properties.tmp
chmod 644 data/velocity/deployment.properties.tmp
update_started=true
mv data/velocity/deployment.properties.tmp data/velocity/deployment.properties

proxy_drained=true
had_players=false
if printf '%s\n' "$running_services" | grep -Eq '^(velocity|minecraft)$'; then
	if wait_for_proxy drained; then
		case "$ack" in
			"$deployment_id true "*) had_players=true ;;
		esac
	else
		proxy_drained=false
		had_players=true
	fi
fi

# Unknown player state also needs a notice. Force does not skip this attempt.
if [ "$had_players" = true ]; then
	notify_update_complete=true
	deployment_status=0
	notice=$(api_post /api/internal/deployment/start) || deployment_status=$?
	if [ "$deployment_status" -ne 0 ]; then
		graceful_failure "Player disconnect or Discord notice failed (status $deployment_status)"
	elif [ "$notice" = false ]; then
		# Older APIs return false when their player list is empty.
		notify_update_complete=false
		graceful_failure "The current API did not send a deployment notice"
	elif [ "$notice" != true ]; then
		graceful_failure "Deployment start did not confirm a Discord notice"
	fi
fi
[ "$proxy_drained" = true ] || graceful_failure "Velocity did not confirm that all players disconnected"

# Update the proxy first so it can show progress while the API and main server restart.
shutdown_attempted=true
rm -f data/velocity/deployment-drained
dc up -d --no-deps --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-600}" velocity
wait_for_proxy drained || graceful_failure "Velocity did not acknowledge the update after startup"

if printf '%s\n' "$running_services" | grep -qx minecraft; then
	# Drain requests and save the world before replacing containers.
	shutdown_started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
	status=0
	api_post /api/internal/shutdown || status=$?
	if [ "$status" -ne 0 ]; then
		echo "API and Minecraft logs from the failed drain/save attempt:" >&2
		dc logs --no-color --timestamps --since "$shutdown_started_at" api minecraft >&2 || true
		if [ "$force" = true ]; then
			graceful_failure "API drain/save failed"
		else
			echo "API drain/save failed. Restart the existing API before aborting." >&2
			dc restart --no-deps api
			# Start the existing container. Do not replace it with the new image.
			dc start --wait --wait-timeout 60 api
			wait_for_proxy ready || { echo "The current server has not recovered." >&2; exit 1; }
			cancel_update
			exit "$status"
		fi
	fi
	dc stop minecraft api
fi

# Remove obsolete website-managed bans from older releases.
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

# Start the release and wait for every health check.
dc up -d --remove-orphans --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-600}"
# Compose cannot detect changes inside configuration bind mounts.
dc up -d --no-deps --force-recreate --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-600}" prometheus grafana loki alloy nginx
wait_for_proxy ready || { echo "Velocity has not confirmed that main is ready." >&2; exit 1; }
clear_update
announce_update_complete || graceful_failure "Could not send the update completion notice"

# Remove unused Docker artifacts. Never prune persistent volumes.
docker container prune -f >/dev/null
docker image prune -f --filter until=168h >/dev/null
docker builder prune -f --filter until=168h >/dev/null

dc ps
