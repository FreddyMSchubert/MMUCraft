# MMUCraft

MMUCraft has four main parts. `services/web` is the Next.js website. `services/api` is the NestJS API and owns authentication, application rules, routing, and SQLite data. `services/velocity` is the public Minecraft entry point. It authenticates players and routes them to backend servers. `minecraft/main/mod` is the Fabric server mod. The mod connects gameplay events and commands to the API through gRPC.

## Development

Install Docker Compose, Git, GNU Make, Python 3.10 or later, JDK 25, and Node.js 24.14.0.

```sh
git submodule update --init --recursive --depth 1
cp .env.example .env
make
```

The root `.env` supplies local values to Docker Compose and to direct API or website commands. Production uses a different `.env` with real secrets, but it follows the same root `.env.example` schema.

The API uses port `8080`. The website uses port `3000`. Velocity uses port `25565`. The main Minecraft server has no public port. The API and website watch source files. Minecraft and Velocity do not watch files.

Grafana uses `http://localhost:3000/grafana/` with username `admin` and the `GRAFANA_ADMIN_PASSWORD` from the root `.env`. If `.env` does not set the password, local development uses `admin`. Anonymous access and Grafana account creation are disabled. The instance contains the Statistics, Gameplay Admin, and Technical dashboards.

Prometheus retains 90 days of history. It scrapes MainMod runtime and JVM metrics, API application and Node.js metrics, and its own health. Production also runs cAdvisor for container metrics and node_exporter for VPS metrics. Monitoring configuration and all three password-gated dashboards are in `monitoring/`.

| Command | Result |
| --- | --- |
| `make` | Build and start all services. Follow all service logs. |
| `make restart` | Restart all running services. |
| `make stop` | Stop all services. |
| `make logs SERVICE=mc` | Print all retained Minecraft logs, then follow new logs. Use `api`, `web`, or `velocity` for another service. |
| `make shell SERVICE=mc` | Open a Minecraft container shell. Use `api`, `web`, or `velocity` for another service. |
| `make console` | Attach to the Minecraft server console. |

`make` invokes item staging, protobuf generation, datagen, the Fabric mod build, the Velocity plugin build, and the resource-pack build. It then builds all images and starts all services.

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

Playwright keeps traces, screenshots, and video for failed tests in `tests/test-results/`. It writes the HTML report to `tests/playwright-report/`.
