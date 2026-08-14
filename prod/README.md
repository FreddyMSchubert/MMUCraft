# Production deployment

The GitHub `deploy` workflow copies this directory to the VPS and runs `deploy.sh`. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. Generate independent values for `AUTH_CODE_SECRET`, `VELOCITY_API_SECRET`, and `VELOCITY_FORWARDING_SECRET`. Do not reuse one secret for another purpose. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

Velocity owns the host Minecraft port. The main Minecraft container has no published host port. Nginx also blocks the Velocity internal API path. Only the proxy can reach that path on the private Compose network.

The first deployment stops the old main-server container before it starts the new stack. This releases port `25565` for Velocity. Expect one normal Minecraft restart during this migration.

### Add a backend server

Start each extra server manually on the production Compose network. Do not publish its Minecraft port. For example, an itzg Fabric server must include FabricProxy-Lite and must receive the production `VELOCITY_FORWARDING_SECRET` value as `FABRIC_PROXY_SECRET`.

This example starts a temporary Fabric backend after you load the production `.env` values into your shell:

```sh
set -a
. ./.env
set +a

docker run -d --name event-server --network kubecraft_app \
  -e EULA=TRUE -e TYPE=FABRIC -e VERSION=26.2 \
  -e ONLINE_MODE=FALSE -e ENFORCE_SECURE_PROFILE=FALSE \
  -e MODRINTH_PROJECTS=fabric-api,fabricproxy-lite \
  -e FABRIC_PROXY_SECRET="$VELOCITY_FORWARDING_SECRET" \
  -v /opt/mmucraft-event:/data \
  itzg/minecraft-server:java25
```

The default production network name is `kubecraft_app`. Use a stable container name or Docker network alias. Then register `<name>:25565` on **Admin > Server monitor**. The name is Docker DNS data; a container ID is not required.

Every backend must trust modern Velocity forwarding before it accepts players. Do not attach an unconfigured offline-mode server to a network that untrusted containers can join.

Removing a server from the admin page does not stop its container or delete its files. Stop and remove that container separately when you no longer need it.

Signups are closed by default. Add one permitted email address per line to `data/api/signup-allowlist.txt`. Email matching is case-insensitive. Put `*` on its own line to permit all valid signup addresses. The API reads the file for each signup attempt, so you do not have to restart it. Sign-in is not affected.

The homepage defaults to the society Discord invite and Instagram account. Override `DISCORD_URL` or `INSTAGRAM_URL` in `prod/.env` if either link changes. The homepage version label is populated automatically from the deployed `IMAGE_TAG`.
