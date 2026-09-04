FROM itzg/minecraft-server:java25

# https://docker-minecraft-server.readthedocs.io/en/latest/variables/
ENV EULA=TRUE
ENV TYPE=FABRIC
ENV VERSION=26.2
ENV ICON=/server-icon.jpg
ENV OVERRIDE_ICON=TRUE

COPY mod/build/libs/*.jar /mods/
COPY config /config/config
COPY server.properties.generated /defaults/server.properties
COPY server-icon.jpg /server-icon.jpg
