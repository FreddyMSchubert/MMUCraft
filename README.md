# MMUCraft

MMUCraft has three main parts. `services/web` is the Next.js website. `services/api` is the NestJS API and owns authentication, application rules, and SQLite data. `minecraft/main/mod` is the Fabric server mod. The mod connects Minecraft events and commands to the API through gRPC.

## Development

Install Docker Compose, Git, GNU Make, Python 3.10 or later, JDK 25, and Node.js 24.14.0.

```sh
git submodule update --init --recursive --depth 1
cp services/api/.env.example services/api/.env
make
```

The API uses port `8080`. The website uses port `3000`. The API and website watch source files. Minecraft does not watch files.

Grafana uses `http://localhost:3001` with username `admin`. Set `GRAFANA_ADMIN_PASSWORD` before `make`; local development defaults to password `admin`. The provisioned Minecraft dashboard shows the live online-player count and 90 days of history.

| Command | Result |
| --- | --- |
| `make` | Build and start all services. Follow all service logs. |
| `make restart` | Restart all running services. |
| `make stop` | Stop all services. |
| `make logs SERVICE=mc` | Print all retained Minecraft logs, then follow new logs. Use `api` or `web` for the other services. |
| `make shell SERVICE=mc` | Open a Minecraft container shell. Use `api` or `web` for the other services. |
| `make console` | Attach to the Minecraft server console. |

`make` invokes item staging, protobuf generation, datagen, the mod build, and the resource-pack build. It then builds all images and starts all services.

Use `make db-generate`, `make db-check`, and `make db-studio` for database work. Commit generated migration files from `services/api/drizzle/`.

## Tests

Install the test dependency and Chromium once:

```sh
npm ci
npx playwright install chromium
```

Run the production-container tests:

```sh
npm test
```

The command creates a `kubecraft-playwright-network` network and a `kubecraft-playwright-database` volume. It does not use `.dev/api` or the development network. The command removes the test containers, network, and database volume when the run ends.

Playwright keeps traces, screenshots, and video for failed tests in `test-results/`. It writes the HTML report to `playwright-report/`.
