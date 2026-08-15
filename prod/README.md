# Production deployment

The GitHub `deploy` workflow copies this directory to the VPS and runs `deploy.sh`. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

Signups are closed by default. Add one permitted email address per line to `data/api/signup-allowlist.txt`. Email matching is case-insensitive. Put `*` on its own line to permit all valid signup addresses. The API reads the file for each signup attempt, so you do not have to restart it. Sign-in is not affected.

The homepage defaults to the society Discord invite and Instagram account. Override `DISCORD_URL` or `INSTAGRAM_URL` in `prod/.env` if either link changes. The homepage version label is populated automatically from the deployed `IMAGE_TAG`.
