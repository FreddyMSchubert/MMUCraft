#!/usr/bin/env bash
set -Eeuo pipefail

: "${RESTIC_REPOSITORY:=/backups/restic}"
: "${RESTIC_HOST:=mmucraft}"
: "${BACKUP_TAG:=mmucraft}"
: "${BACKUP_CHECK_INTERVAL_SECONDS:=300}"
: "${BACKUP_MIN_AGE_SECONDS:=10800}"
: "${BACKUP_MAX_AGE_SECONDS:=21600}"
: "${BACKUP_MIN_FREE_BYTES:=53687091200}"
: "${BACKUP_MIN_FREE_PERCENT:=15}"
: "${BACKUP_KEEP_ALL_WITHIN:=48h}"
: "${BACKUP_KEEP_DAILY_WITHIN:=7d}"
: "${BACKUP_KEEP_WEEKLY_WITHIN:=2m}"
: "${BACKUP_KEEP_MONTHLY:=24}"
: "${BACKUP_KEEP_YEARLY:=5}"
: "${API_BASE_URL:=http://api:8080}"
: "${RESTIC_PASSWORD:?set RESTIC_PASSWORD}"
: "${INTERNAL_API_SECRET:?set INTERNAL_API_SECRET}"

readonly state_dir=/backups/state
readonly lock_dir=/locks
readonly operation_lock="$lock_dir/maintenance.lock"
readonly excludes_file=/etc/mmucraft/backup-excludes.txt

backup_prepared=false

log() {
	printf '%s %s\n' "$(date --iso-8601=seconds)" "$*"
}

api_post() {
	curl \
		--fail-with-body \
		--silent \
		--show-error \
		--max-time 30 \
		--retry 5 \
		--retry-all-errors \
		--retry-delay 5 \
		--header "Authorization: Bearer $INTERNAL_API_SECRET" \
		--request POST \
		"$API_BASE_URL$1"
}

complete_backup() {
	if [ "$backup_prepared" = true ]; then
		if api_post /api/internal/backup/complete >/dev/null; then
			backup_prepared=false
			log 'Minecraft saving resumed through the existing API command path.'
		else
			log 'ERROR: The API did not resume Minecraft saving; restart Minecraft.' >&2
			return 1
		fi
	fi
}

cleanup() {
	status=$?
	trap - EXIT
	complete_backup || true
	exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

initialize_repository() {
	mkdir -p "$RESTIC_REPOSITORY" "$state_dir" "$lock_dir"
	if [ -f "$RESTIC_REPOSITORY/config" ]; then
		restic cat config >/dev/null
		return
	fi
	if find "$RESTIC_REPOSITORY" -mindepth 1 -print -quit | grep -q .; then
		log "ERROR: $RESTIC_REPOSITORY is not empty and is not a Restic repository." >&2
		exit 1
	fi
	log "Initializing local Restic repository at $RESTIC_REPOSITORY."
	restic init
}

latest_snapshot_epoch() {
	latest_time=$(restic snapshots --json --host "$RESTIC_HOST" --tag "$BACKUP_TAG" |
		jq -r 'sort_by(.time) | last | .time // empty')
	if [ -z "$latest_time" ]; then
		printf '0\n'
	else
		date --date="$latest_time" +%s
	fi
}

player_count() {
	api_post /api/internal/backup/status | jq -er '.playerCount'
}

prune_repository() {
	restic forget \
		--host "$RESTIC_HOST" \
		--tag "$BACKUP_TAG" \
		--keep-within "$BACKUP_KEEP_ALL_WITHIN" \
		--keep-within-daily "$BACKUP_KEEP_DAILY_WITHIN" \
		--keep-within-weekly "$BACKUP_KEEP_WEEKLY_WITHIN" \
		--keep-monthly "$BACKUP_KEEP_MONTHLY" \
		--keep-yearly "$BACKUP_KEEP_YEARLY" \
		--prune
}

has_backup_space() {
	read -r total_kib available_kib < <(df -Pk /backups | awk 'NR == 2 { print $2, $4 }')
	if [ -z "${total_kib:-}" ] || [ -z "${available_kib:-}" ]; then
		log 'ERROR: Could not determine backup filesystem free space.' >&2
		return 1
	fi
	available_bytes=$((available_kib * 1024))
	available_percent=$((available_kib * 100 / total_kib))
	if [ "$available_bytes" -lt "$BACKUP_MIN_FREE_BYTES" ] ||
		[ "$available_percent" -lt "$BACKUP_MIN_FREE_PERCENT" ]; then
		log "ERROR: Backup refused: only ${available_percent}% (${available_bytes} bytes) is free; require ${BACKUP_MIN_FREE_PERCENT}% and ${BACKUP_MIN_FREE_BYTES} bytes." >&2
		return 1
	fi
}

record_success() {
	completed_at=$(date +%s)
	printf '%s\n' "$completed_at" >"$state_dir/last-successful.tmp"
	mv "$state_dir/last-successful.tmp" "$state_dir/last-successful"
}

check_repository_if_due() {
	now=$(date +%s)
	last_check=0
	if [ -s "$state_dir/last-check" ]; then
		read -r last_check <"$state_dir/last-check"
	fi
	case "$last_check" in
		'' | *[!0-9]*) last_check=0 ;;
	esac
	if [ $((now - last_check)) -lt 604800 ]; then
		return
	fi
	log 'Running weekly Restic repository check.'
	if restic check; then
		printf '%s\n' "$now" >"$state_dir/last-check.tmp"
		mv "$state_dir/last-check.tmp" "$state_dir/last-check"
	else
		log 'ERROR: Restic repository check failed.' >&2
	fi
}

manual_repository_check() {
	exec 9>"$operation_lock"
	if ! flock -n 9; then
		log 'Deployment or backup is active; refusing the repository check.' >&2
		exec 9>&-
		return 1
	fi
	restic check
	exec 9>&-
}

ensure_saving_enabled() {
	exec 9>"$operation_lock"
	if ! flock -n 9; then
		log 'Deployment or backup is active; leaving Minecraft save state to that operation.'
		exec 9>&-
		return 0
	fi
	backup_prepared=true
	if ! complete_backup; then
		exec 9>&-
		return 1
	fi
	exec 9>&-
}

take_backup() {
	exec 9>"$operation_lock"
	if ! flock -n 9; then
		log 'Deployment or another backup is active; skipping this attempt.'
		exec 9>&-
		return 0
	fi

	log 'Applying retention before the backup.'
	if ! prune_repository; then
		log 'ERROR: Pre-backup retention failed; refusing to write another snapshot.' >&2
		exec 9>&-
		return 1
	fi
	if ! has_backup_space; then
		exec 9>&-
		return 1
	fi

	log 'Asking the API to freeze saves, flush Minecraft, and snapshot SQLite.'
	backup_prepared=true
	if ! api_post /api/internal/backup/prepare >/dev/null; then
		log 'ERROR: The existing API/mod command path could not prepare the backup.' >&2
		complete_backup || true
		exec 9>&-
		return 1
	fi

	log 'Creating the encrypted Restic snapshot.'
	backup_status=0
	restic backup \
		--host "$RESTIC_HOST" \
		--tag "$BACKUP_TAG" \
		--exclude-file "$excludes_file" \
		/source/data/minecraft \
		/source/data/api \
		/source/data/velocity \
		/source/config/.env || backup_status=$?

	complete_status=0
	complete_backup || complete_status=$?
	if [ "$backup_status" -ne 0 ] || [ "$complete_status" -ne 0 ]; then
		log "ERROR: Backup did not complete safely (restic=$backup_status, resume=$complete_status)." >&2
		exec 9>&-
		return 1
	fi

	record_success
	log 'Backup completed successfully; applying retention.'
	prune_repository || log 'ERROR: Post-backup retention failed; the new snapshot is still available.' >&2
	check_repository_if_due
	exec 9>&-
}

scheduled_attempt() {
	now=$(date +%s)
	if ! latest=$(latest_snapshot_epoch); then
		log 'ERROR: Could not determine the newest Restic snapshot.' >&2
		return 1
	fi
	age=$((now - latest))
	if [ "$age" -lt "$BACKUP_MIN_AGE_SECONDS" ]; then
		return 0
	fi

	players='unknown'
	if observed_players=$(player_count); then
		players=$observed_players
	fi
	if [ "$players" != 0 ] && [ "$age" -lt "$BACKUP_MAX_AGE_SECONDS" ]; then
		log "Backup is due but player count is $players; deferring until the server is empty or the snapshot is six hours old."
		return 0
	fi
	if [ "$players" = 0 ]; then
		log 'Backup is due and the server is empty.'
	else
		log "Maximum backup age reached with player count $players; taking a live backup."
	fi
	take_backup
}

run_loop() {
	ensure_saving_enabled
	while true; do
		date +%s >"$state_dir/heartbeat.tmp"
		mv "$state_dir/heartbeat.tmp" "$state_dir/heartbeat"
		if [ "$backup_prepared" = true ]; then
			complete_backup || true
		fi
		scheduled_attempt || true
		sleep "$BACKUP_CHECK_INTERVAL_SECONDS"
	done
}

initialize_repository
case "${1:-run}" in
	run) run_loop ;;
	once)
	take_backup
	;;
	check) manual_repository_check ;;
	*)
		echo "Usage: backup-manager [run|once|check]" >&2
		exit 2
		;;
esac
