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
