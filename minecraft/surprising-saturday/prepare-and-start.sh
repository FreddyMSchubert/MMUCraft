#!/bin/sh
set -eu
cp -a /image/prepared/. /data/
if [ -n "${CHUNKLOCKED_RESOURCE_PACK_URL:-}" ]; then
    sed -i "s|^resource-pack=.*|resource-pack=$CHUNKLOCKED_RESOURCE_PACK_URL|" /config/server.properties
fi
exec /start "$@"
