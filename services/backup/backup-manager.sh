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
: "${BACKUP_FAILURE_HISTORY_LIMIT:=200}"
: "${API_BASE_URL:=http://api:8080}"
: "${RESTIC_PASSWORD:?set RESTIC_PASSWORD}"
: "${INTERNAL_API_SECRET:?set INTERNAL_API_SECRET}"

readonly state_dir=/backups/state
readonly lock_dir=/locks
readonly operation_lock="$lock_dir/maintenance.lock"
readonly excludes_file=/etc/mmucraft/backup-excludes.txt
readonly metrics_dir=/tmp/metrics
readonly metrics_file="$metrics_dir/metrics.txt"
readonly failure_history="$state_dir/failures.tsv"

backup_prepared=false
metrics_server_pid=''
attempt_started_at=0
attempt_number=0

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
	if [ -n "$metrics_server_pid" ]; then
		kill "$metrics_server_pid" 2>/dev/null || true
		wait "$metrics_server_pid" 2>/dev/null || true
	fi
	exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

initialize_repository() {
	mkdir -p "$RESTIC_REPOSITORY" "$state_dir" "$lock_dir" "$metrics_dir"
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

read_state_number() {
	state_path=$1
	default_value=${2:-0}
	state_value=$default_value
	if [ -s "$state_path" ]; then
		read -r state_value <"$state_path"
	fi
	case "$state_value" in
		'' | *[!0-9-]*) state_value=$default_value ;;
	esac
	printf '%s\n' "$state_value"
}

write_state_number() {
	state_name=$1
	state_value=$2
	printf '%s\n' "$state_value" >"$state_dir/$state_name.tmp"
	mv "$state_dir/$state_name.tmp" "$state_dir/$state_name"
}

increment_state_counter() {
	state_name=$1
	state_value=$(read_state_number "$state_dir/$state_name")
	state_value=$((state_value + 1))
	write_state_number "$state_name" "$state_value"
	printf '%s\n' "$state_value"
}

write_metrics() {
	if ! snapshots=$(restic snapshots --json --host "$RESTIC_HOST" --tag "$BACKUP_TAG"); then
		log 'ERROR: Could not refresh backup metrics from Restic.' >&2
		return 1
	fi
	read -r total_kib available_kib < <(df -Pk /backups | awk 'NR == 2 { print $2, $4 }')
	repository_size=$(du -s --block-size=1 "$RESTIC_REPOSITORY" | awk '{ print $1 }')
	last_success=$(read_state_number "$state_dir/last-successful")
	if [ "$last_success" -eq 0 ]; then
		latest_time=$(printf '%s\n' "$snapshots" | jq -r 'sort_by(.time) | last | .time // empty')
		if [ -n "$latest_time" ]; then
			last_success=$(date --date="$latest_time" +%s)
		fi
	fi
	next_attempt=$(read_state_number "$state_dir/next-attempt")
	last_attempt=$(read_state_number "$state_dir/last-attempt")
	last_attempt_success=$(read_state_number "$state_dir/last-attempt-success" -1)
	last_attempt_duration=$(read_state_number "$state_dir/last-attempt-duration")
	attempts_total=$(read_state_number "$state_dir/attempts-total")
	failures_total=$(read_state_number "$state_dir/failures-total")
	in_progress=$(read_state_number "$state_dir/in-progress")
	player_count_value=$(read_state_number "$state_dir/player-count" -1)
	snapshot_count=$(printf '%s\n' "$snapshots" | jq 'length')

	{
		printf '# HELP mmucraft_backup_last_success_timestamp_seconds Unix timestamp of the newest successful backup.\n'
		printf '# TYPE mmucraft_backup_last_success_timestamp_seconds gauge\n'
		if [ "$last_success" -gt 0 ]; then
			printf 'mmucraft_backup_last_success_timestamp_seconds %s\n' "$last_success"
		fi
		printf '# HELP mmucraft_backup_next_attempt_timestamp_seconds Unix timestamp when the manager will next evaluate a backup.\n'
		printf '# TYPE mmucraft_backup_next_attempt_timestamp_seconds gauge\n'
		printf 'mmucraft_backup_next_attempt_timestamp_seconds %s\n' "$next_attempt"
		printf '# HELP mmucraft_backup_last_attempt_timestamp_seconds Unix timestamp when the last backup attempt started.\n'
		printf '# TYPE mmucraft_backup_last_attempt_timestamp_seconds gauge\n'
		printf 'mmucraft_backup_last_attempt_timestamp_seconds %s\n' "$last_attempt"
		printf '# HELP mmucraft_backup_last_attempt_success Whether the last completed attempt succeeded, or -1 before any attempt.\n'
		printf '# TYPE mmucraft_backup_last_attempt_success gauge\n'
		printf 'mmucraft_backup_last_attempt_success %s\n' "$last_attempt_success"
		printf '# HELP mmucraft_backup_last_attempt_duration_seconds Duration of the last completed backup attempt.\n'
		printf '# TYPE mmucraft_backup_last_attempt_duration_seconds gauge\n'
		printf 'mmucraft_backup_last_attempt_duration_seconds %s\n' "$last_attempt_duration"
		printf '# HELP mmucraft_backup_in_progress Whether a backup attempt is currently running.\n'
		printf '# TYPE mmucraft_backup_in_progress gauge\n'
		printf 'mmucraft_backup_in_progress %s\n' "$in_progress"
		printf '# HELP mmucraft_backup_attempts_total Backup attempts since this repository was initialized.\n'
		printf '# TYPE mmucraft_backup_attempts_total counter\n'
		printf 'mmucraft_backup_attempts_total %s\n' "$attempts_total"
		printf '# HELP mmucraft_backup_failures_total Failed backup attempts since this repository was initialized.\n'
		printf '# TYPE mmucraft_backup_failures_total counter\n'
		printf 'mmucraft_backup_failures_total %s\n' "$failures_total"
		printf '# HELP mmucraft_backup_snapshot_count Number of retained Restic snapshots.\n'
		printf '# TYPE mmucraft_backup_snapshot_count gauge\n'
		printf 'mmucraft_backup_snapshot_count %s\n' "$snapshot_count"
		printf '# HELP mmucraft_backup_repository_size_bytes Space occupied by the Restic repository.\n'
		printf '# TYPE mmucraft_backup_repository_size_bytes gauge\n'
		printf 'mmucraft_backup_repository_size_bytes %s\n' "$repository_size"
		printf '# HELP mmucraft_backup_filesystem_free_bytes Free space on the backup filesystem.\n'
		printf '# TYPE mmucraft_backup_filesystem_free_bytes gauge\n'
		printf 'mmucraft_backup_filesystem_free_bytes %s\n' "$((available_kib * 1024))"
		printf '# HELP mmucraft_backup_filesystem_size_bytes Total size of the backup filesystem.\n'
		printf '# TYPE mmucraft_backup_filesystem_size_bytes gauge\n'
		printf 'mmucraft_backup_filesystem_size_bytes %s\n' "$((total_kib * 1024))"
		printf '# HELP mmucraft_backup_player_count Players observed at the last eligibility check, or -1 when unavailable.\n'
		printf '# TYPE mmucraft_backup_player_count gauge\n'
		printf 'mmucraft_backup_player_count %s\n' "$player_count_value"
		printf '# HELP mmucraft_backup_snapshot_size_bytes Logical size of each retained Restic snapshot.\n'
		printf '# TYPE mmucraft_backup_snapshot_size_bytes gauge\n'
		while IFS=$'\t' read -r snapshot_id snapshot_time snapshot_size; do
			[ -n "$snapshot_id" ] || continue
			printf 'mmucraft_backup_snapshot_size_bytes{snapshot="%s",created_at="%s"} %s\n' "$snapshot_id" "$snapshot_time" "$snapshot_size"
		done < <(printf '%s\n' "$snapshots" | jq -r '.[] | [.id[0:8], .time, (.summary.total_bytes_processed // 0)] | @tsv')
		printf '# HELP mmucraft_backup_failure_info Recent failed backup attempts and their reason.\n'
		printf '# TYPE mmucraft_backup_failure_info gauge\n'
		if [ -s "$failure_history" ]; then
			while IFS=$'\t' read -r failure_id failure_time failure_reason; do
				printf 'mmucraft_backup_failure_info{attempt="%s",failed_at="%s",reason="%s"} 1\n' "$failure_id" "$failure_time" "$failure_reason"
			done <"$failure_history"
		fi
	} >"$metrics_file.tmp"
	mv "$metrics_file.tmp" "$metrics_file"
}

start_metrics_server() {
	write_metrics
	httpd -f -p 8080 -h "$metrics_dir" &
	metrics_server_pid=$!
}

update_next_attempt() {
	now=$(date +%s)
	latest=$(latest_snapshot_epoch) || latest=0
	due_at=$((latest + BACKUP_MIN_AGE_SECONDS))
	if [ "$latest" -eq 0 ] || [ "$due_at" -le "$now" ]; then
		next_attempt=$((now + BACKUP_CHECK_INTERVAL_SECONDS))
	else
		remaining=$((due_at - now))
		checks_until_due=$(((remaining + BACKUP_CHECK_INTERVAL_SECONDS - 1) / BACKUP_CHECK_INTERVAL_SECONDS))
		next_attempt=$((now + checks_until_due * BACKUP_CHECK_INTERVAL_SECONDS))
	fi
	write_state_number next-attempt "$next_attempt"
}

begin_attempt() {
	attempt_started_at=$(date +%s)
	attempt_number=$(increment_state_counter attempts-total)
	write_state_number last-attempt "$attempt_started_at"
	write_state_number in-progress 1
	log "event=backup_attempt_started attempt=$attempt_number"
	write_metrics || true
}

finish_failed_attempt() {
	reason=$1
	finished_at=$(date +%s)
	failure_number=$(increment_state_counter failures-total)
	duration=$((finished_at - attempt_started_at))
	failed_at=$(date --date="@$finished_at" --iso-8601=seconds)
	write_state_number last-attempt-success 0
	write_state_number last-attempt-duration "$duration"
	write_state_number in-progress 0
	printf '%s\t%s\t%s\n' "$failure_number" "$failed_at" "$reason" >>"$failure_history"
	tail -n "$BACKUP_FAILURE_HISTORY_LIMIT" "$failure_history" >"$failure_history.tmp"
	mv "$failure_history.tmp" "$failure_history"
	log "event=backup_attempt_finished result=failure attempt=$attempt_number reason=$reason duration_seconds=$duration"
	update_next_attempt
	write_metrics || true
}

finish_successful_attempt() {
	snapshot_id=$1
	finished_at=$(date +%s)
	duration=$((finished_at - attempt_started_at))
	write_state_number last-successful "$finished_at"
	write_state_number last-attempt-success 1
	write_state_number last-attempt-duration "$duration"
	write_state_number in-progress 0
	log "event=backup_attempt_finished result=success attempt=$attempt_number snapshot=$snapshot_id duration_seconds=$duration"
	update_next_attempt
	write_metrics || true
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
	begin_attempt

	log 'Applying retention before the backup.'
	if ! prune_repository; then
		log 'ERROR: Pre-backup retention failed; refusing to write another snapshot.' >&2
		finish_failed_attempt retention
		exec 9>&-
		return 1
	fi
	if ! has_backup_space; then
		finish_failed_attempt insufficient_space
		exec 9>&-
		return 1
	fi

	log 'Asking the API to freeze saves, flush Minecraft, and snapshot SQLite.'
	backup_prepared=true
	if ! api_post /api/internal/backup/prepare >/dev/null; then
		log 'ERROR: The existing API/mod command path could not prepare the backup.' >&2
		complete_backup || true
		finish_failed_attempt prepare
		exec 9>&-
		return 1
	fi

	log 'Creating the encrypted Restic snapshot.'
	backup_status=0
	restic backup \
		--json \
		--host "$RESTIC_HOST" \
		--tag "$BACKUP_TAG" \
		--exclude-file "$excludes_file" \
		/source/data/minecraft \
		/source/data/api \
		/source/data/velocity \
		/source/config/.env | tee "$state_dir/restic-backup.jsonl.tmp" || backup_status=$?
	snapshot_id=$(jq -r 'select(.message_type == "summary") | .snapshot_id // empty' "$state_dir/restic-backup.jsonl.tmp" 2>/dev/null | tail -n 1) || snapshot_id=''
	rm -f "$state_dir/restic-backup.jsonl.tmp"

	complete_status=0
	complete_backup || complete_status=$?
	if [ "$backup_status" -ne 0 ] || [ "$complete_status" -ne 0 ]; then
		log "ERROR: Backup did not complete safely (restic=$backup_status, resume=$complete_status)." >&2
		if [ "$backup_status" -ne 0 ] && [ "$complete_status" -ne 0 ]; then
			failure_reason=restic_and_resume
		elif [ "$backup_status" -ne 0 ]; then
			failure_reason=restic
		else
			failure_reason=resume
		fi
		finish_failed_attempt "$failure_reason"
		exec 9>&-
		return 1
	fi

	log 'Backup completed successfully; applying retention.'
	prune_repository || log 'ERROR: Post-backup retention failed; the new snapshot is still available.' >&2
	check_repository_if_due
	finish_successful_attempt "${snapshot_id:-unknown}"
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
		write_state_number player-count "$players"
	else
		write_state_number player-count -1
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
	write_state_number in-progress 0
	update_next_attempt
	write_metrics || true
	while true; do
		date +%s >"$state_dir/heartbeat.tmp"
		mv "$state_dir/heartbeat.tmp" "$state_dir/heartbeat"
		if [ "$backup_prepared" = true ]; then
			complete_backup || true
		fi
		scheduled_attempt || true
		update_next_attempt
		write_metrics || true
		sleep "$BACKUP_CHECK_INTERVAL_SECONDS"
	done
}

initialize_repository
case "${1:-run}" in
	run)
		start_metrics_server
		run_loop
		;;
	once)
	take_backup
	;;
	check) manual_repository_check ;;
	*)
		echo "Usage: backup-manager [run|once|check]" >&2
		exit 2
		;;
esac
