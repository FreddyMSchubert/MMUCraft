# Surprising Saturday implementation

## Server control

Main stays online. Compose creates one `surprising-saturday` container but does not start it. The `event-controller` service reads the desired state from `GET /api/internal/velocity/event-control` every three seconds. It checks the Docker Compose labels before it starts or stops the named event container. The controller has Docker socket access. Keep this service on the private Compose network and restrict access to its image and configuration.

The desired state follows the active event time window. A committee member can set an override in Admin → Servers. On starts the server for testing. Off requests a stop. Follow event schedule clears the override. The override stays in SQLite after an API restart.

The API tells Velocity to route to the event server only when a live event exists, the committee has not forced it off, and the server responds to a health ping. When the event ends or the committee turns the server off, Velocity routes players to main. The controller waits until Velocity reports no players on the event server. It then waits five more seconds and sends a graceful Docker stop. If Velocity does not report live state, the controller keeps the event server running.

When a committee member turns the server on for testing, any signed-in player can switch to it from the Event tab. Players can also switch during a live event. The API checks their website session, online Minecraft UUID, and server health. Committee player moves use the same Velocity command queue. Velocity sends each command during its next three-second sync. A route change clears old manual moves.

## Event data

`surprising_saturday_events` stores the title, two Markdown descriptions, start and end times, scoring type, and scoring options as JSON. The public page shows the first description before the event starts. It shows the second description when the event starts. This draft accepts `list_completion`. The JSON value is an array of namespaced mob IDs, such as `minecraft:creeper`. The schema leaves room for other scoring types. Add validation and rendering for each new type before use.

`surprising_saturday_participants` records each player who reaches the event server. `surprising_saturday_completions` records the first accepted completion time for each player and mob ID. The leaderboard sorts by the number of completed items. When counts tie, it puts the player who reached that count first ahead. These rows remain after the event ends.

Committee members create and edit future events in Admin → Surprising Saturday. They set the start and end times on the website. The API stores these times in SQLite. The public API hides the title and the second description before the start time. The Event tab shows an upcoming card, a live page, and past events. The live page shows a server switch, a personal checklist, the top three players, and the full leaderboard. It renders Markdown and HTML in a sandboxed frame, so event HTML cannot run script in the website origin.

The Kill Shift template fills the title, both descriptions, and 90 mob IDs from the Killshift coverage list. It does not set event times or create an event. The committee sets and edits the event times in the website form until the event starts.

## Killshift image and API

The repository's Killshift source already targets Minecraft 26.3. The release workflow builds its JAR. It downloads pinned Fabric API, FabricProxy-Lite, and Simple Voice Chat JARs from Modrinth and verifies each SHA-512 hash. The event image build runs the Minecraft base image in setup-only mode and saves the Fabric and game server files in the image. It also copies the mod JARs into `/mods`. The entry script copies the prepared server files into the persistent event world before it starts the server. The event world stays in `data/surprising-saturday`.

On a mob kill, Killshift writes the killer UUID, mob ID, and event time to an outbox file in the persistent event world. It sends pending entries to `POST /api/internal/surprising-saturday/list-completion`. The API accepts only IDs in the event list. The outbox can replay a saved kill after an API outage or a server restart. On player join, Killshift reads `GET /api/internal/surprising-saturday/player-data/:uuid`. It applies the nickname, pronouns, role, and closest Minecraft team color to the player's team label. These calls use the internal bearer secret and do not use gRPC.

## Logs

Docker sends the event server and controller logs to its local log driver. Alloy discovers their Compose service labels and forwards both streams to Loki. The Technical Grafana dashboard has one log panel for each service. The event server uses the Minecraft multiline rule. Loki keeps production logs for 14 days; event results remain in SQLite.

The dev Compose overlay sets a 1 GiB maximum Java heap for each Minecraft server. It starts the main heap at 512 MiB and the event heap at 256 MiB. Each server has a 1.5 GiB container memory limit. Production keeps a 6 GiB main heap and a 4 GiB event heap for its 16 GiB VM.

## Limits in this draft

The profile label refreshes when a player joins; it does not update during a session after a website profile edit. The website supports one scoring type. This workspace did not run a Docker image build, so the setup-only layer and first container start still need a deployment check.
