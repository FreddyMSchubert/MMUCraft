FROM itzg/mc-proxy:java25

RUN cd /tmp \
    && mc-image-helper install-paper --project velocity --version 4.1.2-SNAPSHOT --build 29 --results-file /tmp/velocity.env \
    && . /tmp/velocity.env \
    && mkdir -p /image \
    && cp "$SERVER" /image/velocity.jar

ENV TYPE=CUSTOM \
    CUSTOM_FAMILY=velocity \
    BUNGEE_JAR_FILE=/image/velocity.jar \
    MEMORY=512m \
    REPLACE_ENV_VARIABLES=TRUE \
    HEALTH_HOST=127.0.0.1 \
    SERVER_PORT=25565

ENV PLUGINS=https://cdn.modrinth.com/data/9eGKb6K1/versions/ES87t4lm/voicechat-velocity-2.6.18.jar

COPY config /config
COPY plugin/build/libs/mmucraft-velocity-*.jar /plugins/mmucraft-velocity.jar
