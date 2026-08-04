# Production deployment

The GitHub `deploy` workflow copies this directory to the VPS and runs `deploy.sh`. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

The bundled nginx listener is HTTP-only. Terminate TLS at a provider/load balancer, or bind `HTTP_BIND=127.0.0.1` and proxy to it from the host's TLS-enabled nginx. Set `PUBLIC_URL` to the external HTTPS origin so Minecraft receives the correct resource-pack URL.

The homepage defaults to the society Discord invite and Instagram account. Override `DISCORD_URL` or `INSTAGRAM_URL` in `prod/.env` if either link changes. The homepage version label is populated automatically from the deployed `IMAGE_TAG`.
