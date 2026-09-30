#!/bin/sh
set -eu
cp -a /image/prepared/. /data/
rm -f /data/waiting-room.ready
exec /start "$@"
