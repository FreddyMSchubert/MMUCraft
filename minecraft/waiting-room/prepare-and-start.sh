#!/bin/sh
set -eu
cp -a /image/prepared/. /data/
exec /start "$@"
