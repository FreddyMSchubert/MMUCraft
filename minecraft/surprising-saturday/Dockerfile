FROM itzg/minecraft-server:java25 AS prepared
ENV EULA=TRUE TYPE=FABRIC VERSION=26.3 FABRIC_LOADER_VERSION=0.19.5
RUN SETUP_ONLY=true /start \
    && mkdir -p /image/prepared \
    && cp -a /data/. /image/prepared/

FROM itzg/minecraft-server:java25
ENV EULA=TRUE TYPE=FABRIC VERSION=26.3 FABRIC_LOADER_VERSION=0.19.5
COPY --from=prepared /image/prepared /image/prepared
COPY minecraft/surprising-saturday/mods/fabric-api-*.jar /mods/
COPY minecraft/surprising-saturday/mods/fabricproxy-lite-*.jar /mods/
COPY minecraft/surprising-saturday/chunklocked/build/libs/chunklocked-0.1.0.jar /mods/chunklocked.jar
COPY minecraft/surprising-saturday/server.properties /config/server.properties
COPY minecraft/surprising-saturday/config /config/config
COPY minecraft/main/config/voicechat/voicechat-server.properties /config/config/voicechat/voicechat-server.properties
COPY minecraft/surprising-saturday/prepare-and-start.sh /image/prepare-and-start.sh
RUN sed -i 's/\r$//' /image/prepare-and-start.sh
ENTRYPOINT ["/bin/sh", "/image/prepare-and-start.sh"]
