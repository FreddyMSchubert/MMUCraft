# KubeCraft

## Development

Install Docker Compose, Git, GNU Make, Python 3.10 or later, JDK 25, and Node.js.

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

## Ideas

### General

- we could also do sale vouchers where you have a 20% discount on the item you buy while you have it in the inventory or something
- some sort of way to get ill, which just puts a buncha unstackable slimeballs in your inventory regularly

### Rewards Ideas

- Submit an mp3, and then putting your head on a jokebox plays that sound
- Bedrock Breaker Pickaxe
- Amethyst Cluster Pickaxe
- Submit image to put on hat
- Convo with oderzo about new hat to add
- Ability to put anything in any armor slot
- Make chestplate an elytra
- custom dog skin of choice - should work for all mobs with variants i believe. so cats as well.

### Recommended mods list

Not necessary but helpful

- https://modrinth.com/mod/advancements-reloaded makes the advancements screen much more bearable
- https://modrinth.com/mod/chat-heads for better chatting
- https://modrinth.com/mod/resourcepackcached https://modrinth.com/mod/keep-the-resourcepack one of these two if either of them updates. makes joining less annoying
- https://modrinth.com/mod/appleskin this so you can easily see all the changed food values
- https://modrinth.com/mod/fancy-toasts this if they update cause advancements are important and these are cool and fancy

theres even a modrinth server thing we could set up so you dont have to install everything yourself might be cool if its possible
