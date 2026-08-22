# KubeCraft

## Development

Install Docker Compose, Git, GNU Make, Python 3.10 or later, JDK 25, and Node.js 24.14.0.

```sh
git submodule update --init --recursive --depth 1
cp services/api/.env.example services/api/.env
make
```

The API uses port `8080`. The website uses port `3000`. The API and website watch source files. Minecraft does not watch files.

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
