#!/bin/sh
set -eu
cp -a /image/prepared/. /data/
paper_jar=$(find /image/prepared -maxdepth 1 -type f -name 'paper-*.jar' -print -quit)
[ -n "$paper_jar" ] || { echo 'Prepared Paper jar is missing' >&2; exit 1; }
export PAPER_CUSTOM_JAR="/data/${paper_jar##*/}"
rm -f /data/waiting-room.ready
exec /start "$@"
