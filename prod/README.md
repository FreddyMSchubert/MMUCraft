# Production deployment

The GitHub `deploy` workflow copies this directory to the VPS and runs `deploy.sh`. Runtime state stays under `data/`, which is not present in the deployment archive, and Docker volumes are never pruned.

The API applies pending Drizzle migrations before it starts listening. The image contains them at `/app/drizzle`, while `/app/data/app.sqlite` is the mounted production database; a failed migration makes the API fail instead of serving against the wrong schema.

On the VPS, copy `.env.example` to `.env`, fill every value, and keep the file readable only by the deployment user. The deployment user must be in the `docker` group and its numeric UID/GID should match `PUID`/`PGID`.

Signups are closed by default. Add one permitted email address per line to `data/api/signup-allowlist.txt`. Email matching is case-insensitive. Put `*` on its own line to permit all valid signup addresses. The API reads the file for each signup attempt, so you do not have to restart it. Sign-in is not affected.

## One-time Let's Encrypt setup

The production Nginx container serves HTTPS and redirects HTTP and `www` to the host in `PUBLIC_URL`. Certbot only creates and renews the host certificate.

Replace `example.com` and the email address below.

1. Point the `A` records for `example.com` and `www.example.com` to the VPS. Add `AAAA` records only if public IPv6 works on the VPS.
2. Permit inbound TCP ports `80` and `443` in the cloud firewall and the VPS firewall.
3. Set `PUBLIC_URL=https://example.com` in `/opt/mmucraft/.env`.
4. Install Certbot on Debian or Ubuntu:

   ```sh
   sudo apt update
   sudo apt install -y certbot
   ```

   Do not install host Nginx. If you installed it from the earlier guide, run `sudo systemctl disable --now nginx` before step 5.

5. Get one certificate for both names. The saved hooks stop the production Nginx container only while Certbot renews the certificate:

   ```sh
   sudo certbot certonly --standalone \
     --cert-name mmucraft \
     --email admin@example.com \
     --agree-tos --no-eff-email \
     --pre-hook "docker ps -q --filter label=com.docker.compose.project=kubecraft --filter label=com.docker.compose.service=nginx | xargs -r docker stop" \
     --post-hook "docker ps -aq --filter label=com.docker.compose.project=kubecraft --filter label=com.docker.compose.service=nginx | xargs -r docker start" \
     -d example.com -d www.example.com
   ```

Run the GitHub `deploy` workflow. The production setup mounts only the `mmucraft` certificate directories, listens on ports `80` and `443`, and uses that certificate.

Verify the result:

```sh
sudo certbot renew --dry-run
curl -I 'http://example.com/test?x=1'
curl -I 'https://www.example.com/test?x=1'
curl -I https://example.com/api/health
```

The first two requests must redirect to `https://example.com/test?x=1`. Renewal briefly stops the Nginx container because Certbot standalone must use port `80`.

The Docker credential warning in GitHub Actions is not the failure. The workflow uses a short-lived token and logs out after deployment. The original exit code came from `PUBLIC_URL` not starting with `https://` in `/opt/mmucraft/.env`.
