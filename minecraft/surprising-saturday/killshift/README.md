# Killshift

Killshift is a server-side Fabric mod for Minecraft Java Edition 26.3.

When a player kills a mob, the player becomes that mob. The player returns to normal after death or after killing another player.

Author: @FreddyMSchubert

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or later
- Fabric API 0.161.0+26.3
- Java 25 or later

Clients do not need to install Killshift. Install the mod and Fabric API on the server. You can also use the mod in a local Fabric game.

## Build

Run this command from the project directory:

```sh
./gradlew build
```

The command creates `build/libs/killshift-0.1.0.jar`.

## Core rules

- A direct mob kill changes the killer into the dead mob.
- A projectile kill counts when Minecraft reports the player as the projectile owner.
- A transformed player returns to normal on death.
- A transformed player returns to normal after killing a player.
- A mob still creates its normal loot.
- Mob equipment is copied into the killer's inventory.
- Every form has at least one point of attack damage.
- Sneak once to play the form's ambient sound.

## Movement and combat

Killshift reads health, attack damage, armor, and eye height from the killed mob. It scales the player so the camera reaches the mob's eye height. This also changes the player hitbox. Killshift does not copy the mob movement-speed value. A mob and a player use that value in different movement systems. A direct copy makes common mobs much too fast or much too slow.

Most land forms use normal player speed. Naturally quick forms use a small speed multiplier. Aquatic forms use normal player speed on land and receive full water movement efficiency.

Flying forms use creative flight. Killshift forces flight on forms that cannot normally walk, such as allays. Horse forms and other strong jumpers receive a jump-strength multiplier.

Killshift keeps attack damage at one or more. A weak form can therefore kill another mob and change form again.

## Visual disguise

Killshift makes the real player invisible. It creates a silent, invulnerable, no-AI copy of the killed mob. The copy has no physics and follows the player. A no-collision team stops the copy from pushing players on the server and on clients.

Other players see the full-size copy at the player position. The owner receives a smaller scale for the copy. The smaller model stays below the first-person camera and remains visible in third-person view. Killshift sends the copy position to tracking players each tick. The server does not receive the client's camera mode.

Attacks against the visible copy are redirected to its owner. The copy cannot push the player and cannot change player movement.

## Code structure

- `Killshift` registers Fabric events.
- `ShapeManager` owns transform, reset, combat redirection, equipment copy, and attribute lifecycle.
- `MobRegistry` assigns passive traits and form statistics.
- `ShapeRuntime` applies passive behavior on each server tick.
- `ShapeView` owns the world model, collision rule, and owner-only scale.
- `MobAbilities` owns active right-click actions and on-hit effects.
- `MobMixin` stops friendly mob families from targeting matching player forms.

This split keeps entity display code out of movement code. It also keeps temporary attributes in one lifecycle. A reset can therefore remove every Killshift modifier without changing unrelated modifiers from other mods.

See [docs/MOBS.md](docs/MOBS.md) for the complete behavior list.

## Current limits

- Right-click abilities run only when the used hand is empty. This rule lets normal item use run first.
- The current build stores forms in memory. A server restart clears all forms.
- A view that must be recreated after a dimension change keeps its mob type but may lose source-specific variant data.
- Some mobs have only the common form behavior. The mob list marks these cases.
- Skeleton arrow refill keeps one arrow in the inventory. A player can remove that arrow, so this is not an anti-duplication system.

## Licensing

This project does not declare a code license. No license is granted by default.

## MMUCraft event API

The Surprising Saturday image sets `SURPRISING_SATURDAY_API_URL` and `SURPRISING_SATURDAY_API_SECRET`. Killshift saves each pending kill to `/data/killshift-score-outbox.jsonl` and sends it to the API. It retries after a failure. The API stores the completion list and calculates the score. The mod also reads player presentation data when a player joins. The mod works without these variables, but it does not send event data.
