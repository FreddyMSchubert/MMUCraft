# Deployment operations

For a new server, follow the [complete setup and cutover guide](SETUP.md).

The GitHub `deploy` workflow copies this directory, the root `.env.example`, and the monitoring configuration to the selected server. It then runs `deploy.sh` with the `dev` or `production` target. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

Generate separate values for `VELOCITY_API_SECRET` and `VELOCITY_FORWARDING_SECRET`. The API secret authenticates private internal control requests from Velocity and the backup manager. The forwarding secret proves that a backend connection came through Velocity. Do not publish either secret.

## Automatic backups

Production runs an encrypted, deduplicated Restic backup sidecar. Before the first deployment, add an independently generated repository password to the server's existing `.env`:

```sh
BACKUP_RESTIC_PASSWORD=<output of: openssl rand -hex 32>
```

Keep `BACKUP_RESTIC_PASSWORD` in a password manager outside this server. A copied Restic repository cannot be restored without it.

The dev Compose overlay does not define or run the backup service, does not require this password, and does not create a local backup repository.

The manager checks every five minutes. Once the newest successful snapshot is three hours old, it backs up as soon as the Minecraft server is empty. At six hours it takes a live backup even if players remain online. It asks the existing authenticated API and Minecraft mod command path to run `save-off` and `save-all flush`; the API also creates and verifies a logical SQLite snapshot. It always asks that same path to run `save-on` before it finishes. RCON is not enabled or used. Deployments and backups use `data/locks/maintenance.lock`, so they cannot modify the server at the same time.

Snapshots contain `data/minecraft`, non-database files in `data/api`, `data/velocity`, `.env`, and the consistent SQLite snapshot at `data/api/backup-staging/app.sqlite`. Reconstructable Minecraft downloads, logs, caches, the live WAL database files, temporary staging files, and monitoring history are excluded. Prometheus and Loki retention remains independent of disaster-recovery backups.

The local repository is at `backups/restic`. A backup proceeds only while that filesystem has at least 50 GiB and 15% available. Before each new snapshot, expired snapshots are pruned using this policy:

- all snapshots from the last 48 hours;
- one snapshot per day through the last 7 days;
- one snapshot per week through the last 2 months;
- one snapshot per month for 24 months;
- one snapshot per year for 5 years.

Restic applies all retention rules as a union, so longer-lived daily, weekly, monthly, and yearly snapshots remain after the denser snapshots expire. The manager runs `restic check` weekly.

Grafana provisions an **MMUCraft Backups** dashboard in the Admin folder. It shows the last successful backup age, the next eligibility check, manager health and activity, retained snapshots, recent failure reasons, repository size, free disk space, attempt history, and backup logs. These metrics are exposed only by the production backup container on the private Compose network; the dev stack has neither the service nor the Prometheus scrape target.

Useful production commands, run from the deployment directory, are:

```sh
# Status and recent logs
docker compose --env-file .env --env-file .release.env ps backup
docker compose --env-file .env --env-file .release.env logs --tail 100 backup

# List snapshots or request an immediate safe snapshot
docker compose --env-file .env --env-file .release.env exec backup restic snapshots
docker compose --env-file .env --env-file .release.env exec backup backup-manager once

# Run an integrity check now
docker compose --env-file .env --env-file .release.env exec backup backup-manager check
```

For a restore drill, do not write directly over the live server. Restore into a new host directory and inspect it first:

```sh
mkdir -p restore-test
docker compose --env-file .env --env-file .release.env run --rm --no-deps \
  -v "$PWD/restore-test:/restore" --entrypoint restic backup \
  restore latest --tag mmucraft --host mmucraft-production --target /restore
```

The restored tree contains the original absolute container paths beneath `restore/source/...`, including the database snapshot at `restore/source/data/api/backup-staging/app.sqlite`. Stop the game, API, Velocity, and backup services before copying selected restored files into `data/`. Replace `data/api/app.sqlite` with the restored backup-staging copy; do not restore stale `app.sqlite-wal` or `app.sqlite-shm` files. Perform a restore drill at least quarterly.

When manually copying the on-device repository elsewhere, stop the backup service first, copy the complete `backups/restic` directory, and then start the service again. Minecraft can remain online during that repository copy. This prevents the external copy from observing a Restic write halfway through.

## Minecraft network

Velocity owns the public Minecraft port. Backend servers have no published port. They use offline mode because Velocity authenticates the Mojang account. FabricProxy-Lite verifies the forwarding secret and restores the authenticated UUID, username, skin, and client address on each Fabric backend.

Simple Voice Chat uses UDP port `24454` on Velocity. The shared Compose file publishes this port on `MINECRAFT_BIND`. Allow inbound UDP `24454` in UFW and any provider firewall. Keep the Minecraft backend ports private. Voice packets go from the client to Velocity, then to the backend over the Docker network. HTTP proxies do not carry this UDP traffic.

The images include the voice configuration. The backend enables 48-block proximity audio, 24-block whispers, and group chat. Players must install the client mod to use audio. See the root README for client setup. Deploy the updated Minecraft and Velocity images with the normal deployment workflow. After deployment, use `/voicechat test <player>` and two modded clients to check audio. Container health checks do not verify voice traffic.

Install Simple Voice Chat on each temporary or event backend too. Permit UDP traffic from Velocity to that backend on its configured voice port. Separate containers can each use port `24454`. Servers that share one network address must use different voice ports.

The database migration creates the `main` backend. The Velocity configuration has no static backend. If the MMUcraft Velocity plugin does not start, the proxy cannot route a player to a backend.

The first Velocity deployment saves `banned-players.json.pre-velocity`. It removes only Minecraft ban entries that the old website blacklist created. It keeps bans that an operator or the console created.

Velocity asks the API for an access decision during each login. The API checks maintenance mode, the website account, and active bans. A player in an active signup flow receives the signup code in the Minecraft disconnect screen. If the API is unavailable, Velocity rejects the login. Connected players stay online.

Every three seconds, Velocity sends backend health, online players, and completed move commands to the API. The API returns the server registry, active route, manual moves, maintenance state, and players to disconnect. Velocity pings the registered backends. The API does not run a separate backend ping loop.

Use the Admin **Servers** page to add one temporary or event server. Start that backend without a public port. Attach it to the `kubecraft_app` Docker network. Install Fabric API and FabricProxy-Lite `2.12.0`, and set `FABRIC_PROXY_SECRET` to the production forwarding secret. Add its Docker address, such as `event-server:25565`, in the website. Removing the registry entry does not stop or delete the backend container.

The Servers page also shows online players and backend health. A manual move lasts until the player disconnects or the active route changes. A schedule temporarily replaces the default route. At its start, connected players move to the scheduled server. At its end, they move to the current default server. Players who join during the schedule also use its server. The system does not fall back to the default server if the scheduled server is offline. The Maintenance page disconnects current players and rejects new logins during the next control sync.

Signups are closed by default. Add one permitted email address per line to `data/api/signup-allowlist.txt`. Email matching is case-insensitive. Put `*` on its own line to permit all valid signup addresses. The API reads the file for each signup attempt, so you do not have to restart it. Sign-in is not affected.

The homepage defaults to the society Discord invite and Instagram account. Override `DISCORD_URL` or `INSTAGRAM_URL` in the deployment `.env` if either link changes. The homepage version label uses the deployed `IMAGE_TAG`. Release images also show the release name and link to the GitHub release.

Grafana is available at `https://grafana.PUBLIC_HOST/`. Requests to the old `/grafana/` path redirect to this host. Sign in as `admin` with `GRAFANA_ADMIN_PASSWORD` from `.env`. Anonymous access and Grafana account creation are disabled. Website accounts are not affected.

Grafana contains the Statistics, Gameplay Admin, and Technical dashboards. The Technical dashboard shows container logs and lets you filter them by service. Production Prometheus retains one year of metrics, and production Loki retains 14 days of logs. Development keeps one day in each service. Both services store their data in Docker volumes.

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
python3 deploy/check-deployment.py
```

This check uses temporary command substitutes. It does not start Docker or contact a server.

## Replacement server limits

The main server owns one persistent world at `data/minecraft`. A second Minecraft process cannot safely use that live world. A separate world copy would become stale while players continue to play. A safe switch would require a final save, a consistent copy, and a new server start. The API also owns one SQLite database and applies migrations at startup. Two releases would need compatible database access.

For this setup, keep one main server. Pull images before downtime, save and stop the current server, then start the replacement. Compose recreates application containers when their image or configuration changes. The API is stopped after its drain so it starts accepting requests again even when its image has not changed. Nginx and the monitoring services are recreated to load changes in their mounted configuration files. Compose cannot detect changes inside those files.
