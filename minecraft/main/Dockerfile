FROM itzg/minecraft-server:java25

# https://docker-minecraft-server.readthedocs.io/en/latest/variables/
ENV EULA=TRUE
ENV TYPE=FABRIC
ENV VERSION=26.2

COPY mod/build/libs/*.jar /mods/
COPY server.properties.generated /defaults/server.properties
