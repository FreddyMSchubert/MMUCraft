FROM itzg/mc-proxy:java25

ENV TYPE=VELOCITY \
    VELOCITY_VERSION=latest \
    MEMORY=512m \
    REPLACE_ENV_VARIABLES=TRUE

COPY config /config
COPY plugin/build/libs/mmucraft-velocity-*.jar /plugins/mmucraft-velocity.jar
