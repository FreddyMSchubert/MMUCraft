FROM itzg/minecraft-server:java25 AS prepared
ENV EULA=TRUE TYPE=PAPER VERSION=26.3
RUN SETUP_ONLY=true /start \
    && mkdir -p /image/prepared \
    && cp -a /data/. /image/prepared/

FROM itzg/minecraft-server:java25
ENV EULA=TRUE TYPE=PAPER VERSION=26.3
COPY --from=prepared /image/prepared /image/prepared
COPY minecraft/waiting-room/plugin/build/libs/mmucraft-waiting-room-1.0.0.jar /plugins/waiting-room.jar
COPY minecraft/waiting-room/server.properties /config/server.properties
COPY minecraft/waiting-room/bukkit.yml /config/bukkit.yml
COPY minecraft/waiting-room/paper-global.yml /config/config/paper-global.yml
COPY minecraft/waiting-room/prepare-and-start.sh /image/prepare-and-start.sh
ENTRYPOINT ["/bin/sh", "/image/prepare-and-start.sh"]
