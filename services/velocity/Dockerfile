FROM itzg/mc-proxy:java25

ENV TYPE=VELOCITY \
    VELOCITY_VERSION=4.1.1 \
    VELOCITY_BUILD_ID=24 \
    MEMORY=512m \
    REPLACE_ENV_VARIABLES=TRUE \
    HEALTH_HOST=127.0.0.1 \
    SERVER_PORT=25565

COPY config /config
COPY plugin/build/libs/mmucraft-velocity-*.jar /plugins/mmucraft-velocity.jar
