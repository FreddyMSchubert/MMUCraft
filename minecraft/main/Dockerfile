FROM itzg/minecraft-server:latest

# https://docker-minecraft-server.readthedocs.io/en/latest/variables/
ENV EULA=TRUE
ENV TYPE=FABRIC
ENV VERSION=26.1

COPY mod/build/libs/*.jar /mods/
COPY server.properties.generated /config/server.properties
