FROM itzg/minecraft-server:java25 AS prepared
ENV EULA=TRUE TYPE=PAPER VERSION=26.3 PAPER_CUSTOM_JAR=/data/paper-26.3-140.jar
ADD --checksum=sha256:98aabc113a80b9b5e183475e839a17cf99c39c915a1f46b8f35a5e89fd5de0f1 https://fill-data.papermc.io/v1/objects/98aabc113a80b9b5e183475e839a17cf99c39c915a1f46b8f35a5e89fd5de0f1/paper-26.3-140.jar /data/paper-26.3-140.jar
RUN SETUP_ONLY=true /start \
    && mkdir -p /image/prepared \
    && cp -a /data/. /image/prepared/

FROM itzg/minecraft-server:java25
ENV EULA=TRUE TYPE=PAPER VERSION=26.3
ENV PLUGINS=https://cdn.modrinth.com/data/9eGKb6K1/versions/EJth3OAr/voicechat-bukkit-2.6.24.jar
COPY --from=prepared /image/prepared /image/prepared
COPY minecraft/waiting-room/plugin/build/libs/mmucraft-waiting-room-1.0.0.jar /plugins/waiting-room.jar
COPY minecraft/waiting-room/server.properties /config/server.properties
COPY minecraft/waiting-room/bukkit.yml /config/bukkit.yml
COPY minecraft/waiting-room/paper-global.yml /config/config/paper-global.yml
COPY minecraft/waiting-room/prepare-and-start.sh /image/prepare-and-start.sh
ENTRYPOINT ["/bin/sh", "/image/prepare-and-start.sh"]
