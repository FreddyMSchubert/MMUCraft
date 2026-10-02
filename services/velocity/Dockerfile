FROM itzg/mc-proxy:java25

ADD --checksum=sha256:35a5596a5468a035d8a32c8de5ebb0dc6b8d8f0cc3ff5169d514aca762af8aa8 https://fill-data.papermc.io/v1/objects/35a5596a5468a035d8a32c8de5ebb0dc6b8d8f0cc3ff5169d514aca762af8aa8/velocity-4.2.0-30.jar /image/velocity.jar
RUN chmod 755 /image && chmod 644 /image/velocity.jar \
    && gosu bungeecord sh -c 'test -s /image/velocity.jar && test -r /image/velocity.jar'

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
