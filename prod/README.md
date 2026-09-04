# Production deployment

The GitHub `deploy` workflow copies this directory, the root `.env.example`, and the monitoring configuration to the VPS. It then runs `deploy.sh`. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

Generate separate values for `VELOCITY_API_SECRET` and `VELOCITY_FORWARDING_SECRET`. The API secret authenticates private Velocity control requests. The forwarding secret proves that a backend connection came through Velocity. Do not publish either secret.

## Minecraft network

Velocity owns the public Minecraft port. Backend servers have no published port. They use offline mode because Velocity authenticates the Mojang account. FabricProxy-Lite verifies the forwarding secret and restores the authenticated UUID, username, skin, and client address on each Fabric backend.

The database migration creates the `main` backend. The Velocity configuration has no static backend. If the MMUcraft Velocity plugin does not start, the proxy cannot route a player to a backend.

The first Velocity deployment saves `banned-players.json.pre-velocity`. It removes only Minecraft ban entries that the old website blacklist created. It keeps bans that an operator or the console created.

Velocity asks the API for an access decision during each login. The API checks maintenance mode, the website account, and active bans. A player in an active signup flow receives the signup code in the Minecraft disconnect screen. If the API is unavailable, Velocity rejects the login. Connected players stay online.

Every three seconds, Velocity sends backend health, online players, and completed move commands to the API. The API returns the server registry, active route, manual moves, maintenance state, and players to disconnect. Velocity pings the registered backends. The API does not run a separate backend ping loop.

Use the Admin **Servers** page to add one temporary or event server. Start that backend without a public port. Attach it to the `kubecraft_app` Docker network. Install Fabric API and FabricProxy-Lite `2.12.0`, and set `FABRIC_PROXY_SECRET` to the production forwarding secret. Add its Docker address, such as `event-server:25565`, in the website. Removing the registry entry does not stop or delete the backend container.

The Servers page also shows online players and backend health. A manual move lasts until the player disconnects or the active route changes. A schedule temporarily replaces the default route. At its start, connected players move to the scheduled server. At its end, they move to the current default server. Players who join during the schedule also use its server. The system does not fall back to the default server if the scheduled server is offline. The Maintenance page disconnects current players and rejects new logins during the next control sync.

Signups are closed by default. Add one permitted email address per line to `data/api/signup-allowlist.txt`. Email matching is case-insensitive. Put `*` on its own line to permit all valid signup addresses. The API reads the file for each signup attempt, so you do not have to restart it. Sign-in is not affected.

The homepage defaults to the society Discord invite and Instagram account. Override `DISCORD_URL` or `INSTAGRAM_URL` in the deployment `.env` if either link changes. The homepage version label is populated automatically from the deployed `IMAGE_TAG`.

Grafana is available at `https://grafana.PUBLIC_HOST/`. Requests to the old `/grafana/` path redirect to this host. Sign in as `admin` with `GRAFANA_ADMIN_PASSWORD` from `.env`. Anonymous access and Grafana account creation are disabled. Website accounts are not affected.

Grafana contains the Statistics, Gameplay Admin, and Technical dashboards. The Technical dashboard shows container logs and lets you filter them by service. Prometheus retains 90 days of metrics, and Loki retains 14 days of logs. Both services store their data in Docker volumes.

## Update sequence

Images are pulled and checked before the warning period. The script sends the warning only if the main server has players, then writes `data/velocity/deployment.properties`. The file contains `updating=true`, a start time in Unix seconds, and a unique deployment ID. The script writes a temporary file and renames it so Velocity cannot read a partial update.

Velocity reads this file without the API. It blocks new joins and disconnects connected players with an update message. It writes `deployment-drained` only after all proxy connections have closed. This file contains the deployment ID, whether players were connected, and whether the main server and route are ready. The deployment script rejects an acknowledgement from a different deployment.

If players were connected, the API sends a Discord start notice and a completion notice. If Velocity confirms that no players were connected, the script skips both notices. If the player state is unknown, the script attempts a notice. `force=true` still attempts notices, disconnects, and a world save. It continues after a failed attempt and writes a warning to the deployment log. Without force, a failed attempt stops deployment. Force does not bypass release health checks.

The script updates Velocity before it stops the API and Minecraft. Velocity has no API startup dependency. It can show update progress while those services restart. A Velocity image change still causes a short proxy restart. During that restart, the proxy cannot show a message.

The server list shows the update status. Each connection attempt shows the elapsed update time and an estimate of 200–300 seconds. After 10 minutes, the message asks players to contact the committee. It states whether the main server responds. The time is refreshed on each request; a disconnect screen does not update after the connection closes.

The script clears the update flag after the services pass their health checks and Velocity confirms that the main server and route are ready. If a deployment fails after shutdown starts, the flag stays active. This prevents a failed update from appearing complete. Run a successful deployment to clear this state. If deployment stops before shutdown, the script clears the flag. If the API fails to save, a normal deployment first restarts the existing API and checks recovery before it clears the flag.

The first deployment of this change requires `force=true` because the old Velocity plugin cannot write an acknowledgement. The script still calls the old API to attempt its disconnect and notice. The new behaviour is available after the proxy has been updated. Later deployments can use `force=false`.

Run the local deployment check with:

```sh
python3 prod/check-deployment.py
```

This check uses temporary command substitutes. It does not start Docker or contact a server.

## Replacement server limits

The main server owns one persistent world at `data/minecraft`. A second Minecraft process cannot safely use that live world. A separate world copy would become stale while players continue to play. A safe switch would require a final save, a consistent copy, and a new server start. The API also owns one SQLite database and applies migrations at startup. Two releases would need compatible database access.

For this setup, keep one main server. Pull images before downtime, save and stop the current server, then start the replacement. Compose recreates application containers when their image or configuration changes. The API is stopped after its drain so it starts accepting requests again even when its image has not changed. Nginx and the monitoring services are recreated to load changes in their mounted configuration files. Compose cannot detect changes inside those files.
