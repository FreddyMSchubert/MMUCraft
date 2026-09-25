# Killshift

Killshift is a server-side Fabric mod for Minecraft Java Edition 26.3.

When a player kills a mob, the player becomes that mob. A player kill copies the victim's form. If the victim has no mob form, the killer gets a mannequin with the victim's skin. Death restores the player's normal form.

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
- The killer moves to the dead entity's position.
- A transformed player returns to normal on death.
- A player kill gives the killer a player form with the dead player's skin.
- If the dead player has a mob form, the killer gets that mob form and its appearance.
- A form stays active when the player leaves and rejoins.
- On a form change, the old display stays in the world. It receives the player's health and effects, and its mob AI resumes.
- A mob killed for a shift drops no items or experience.
- Each new creeper has a 20% chance to be charged. Each spawned zombie has a 1% chance to become a giant.
- Giants use zombie pursuit and target goals. They keep giant health, speed, and attack damage.
- The display keeps the source mob's equipment. The kill does not add that equipment to the killer's inventory.
- Every form has at least one point of attack damage.
- Each new sneak press plays the form's ambient sound. Holding sneak plays it once.
- A player respawns near the death location in the same dimension. Members and Committee have a 100-block radius. Other players have a 200-block radius.

## Movement and combat

Killshift reads the killed entity's current attribute values. These include health, armor, damage, knockback, gravity, jump strength, movement speed, safe fall distance, and step height. It converts mob movement speed for player controls. The player scale places the camera above the source model. It also changes the player hitbox.

Aquatic forms gain full water movement efficiency. Fish and bees have no land movement speed. Water-only forms can jump on land but gain little horizontal motion while airborne. Flying forms use player flight controls. Killshift forces flight each tick for these forms, including allays and happy ghasts. Chicken forms fall slowly. Mob forms cannot use player sprint or swim movement. Iron golems sink in water and keep their air supply.

Killshift keeps attack damage at one or more. A weak form can therefore kill another mob and change form again.

Every form can eat beetroot and beetroot soup. If a player tries to eat another forbidden food, Killshift names the food and lists the form's allowed foods.

## Visual disguise

Killshift makes the real player invisible. It creates a silent, invulnerable, no-AI copy of the killed mob. The copy has no physics and follows the player. A no-collision team stops the copy from pushing players on the server and on clients. The form retains the source entity's saved visual data, such as its variant.

Other players see the full-size copy at the player position. The owner receives a tiny copy. Killshift keeps the source cube size and baby age. The model stays below the first-person camera and remains visible in third-person view. Killshift moves the copy each server tick. Minecraft tracks and interpolates its position for clients. The server does not receive the client's camera mode.

Attacks against the visible copy are redirected to its owner. The copy cannot push the player and cannot change player movement.

Killshift does not save active display entities with the world. It removes old orphan displays when their chunks load. This prevents stationary no-AI copies after a restart.

The locator bar shows players in the same dimension at any distance. Sneaking, invisibility, equipment, spectator mode, and transformation do not hide their icons.

The ninth hotbar slot always contains a marked item. Each active ability has a matching item, such as TNT for creepers and an ender pearl for endermen. Other forms hold a barrier. Right-click with the marked item while aiming at air, a block, or an entity. The item stays in that slot. A creeper dies after its explosion.

The server adds creeper, spider, and invert post effects to matching forms. It removes a form's effect when the form ends. A vanilla 26.3 client can display these effects.

## Code structure

- `Killshift` registers Fabric events.
- `ShapeManager` owns transform, reset, combat redirection, and attribute lifecycle.
- `MobRegistry` assigns passive traits and form statistics.
- `ShapeRuntime` applies passive behavior on each server tick.
- `ShapeView` owns the world model, collision rule, and owner-only scale.
- `MobAbilities` owns active right-click actions and on-hit effects. `AbilitySlot` reserves the ninth hotbar slot.
- `MobFood` limits food use to the form's diet.
- `NearbyRespawn` finds a safe position near the death location.
- `ShiftCommand` lets an administrator set a player or mob form.
- `LocatorVisibility` keeps player locator icons visible.
- `ShapeEffects` manages post effects.
- `MobMixin` stops friendly mob families from targeting matching player forms.

This split keeps entity display code out of movement code. It also keeps temporary attributes in one lifecycle. A reset can therefore remove every Killshift modifier without changing unrelated modifiers from other mods.

See [docs/MOBS.md](docs/MOBS.md) for the complete behavior list.

## Current limits

- Some abilities need a target or valid terrain. For example, the guardian beam needs a living target in sight, and the shulker needs a safe teleport location.
- Vex movement uses server no-physics mode. Vanilla client movement can still stop the player at a wall. Full spectator movement also grants other spectator powers.
- The display entity follows the player each tick. Network interpolation can still cause visual delay.
- Some mobs have only the common form behavior. The mob list marks these cases.
- Skeleton arrow refill keeps one arrow in the inventory. A player can remove that arrow, so this is not an anti-duplication system.
- If no safe location exists in the allowed respawn area, Minecraft keeps its normal respawn position.

## Admin shift command

Players with gamemaster permission can use these forms:

```text
/shift creeper
/shift Alice creeper
/shift Alice
/shift Bob Alice
```

The first form shifts the administrator into a mob. The second shifts Alice into a mob. The third gives the administrator a mannequin with Alice's skin. The fourth gives Bob a mannequin with Alice's skin. Alice's current mob form does not affect these commands. Names must refer to online players. These commands do not record a kill or change event score.

## Licensing

This project does not declare a code license. No license is granted by default.

## MMUCraft event API

The Surprising Saturday image sets `SURPRISING_SATURDAY_API_URL` and `SURPRISING_SATURDAY_API_SECRET`. Killshift saves each pending kill to `/data/killshift-score-outbox.jsonl` and sends it to the API. It retries after a failure. The API stores listed kills and calculates the score. The completion reply contains the current score and scored mobs. Killshift tells the killer those results after each kill during a live event. Unlisted mobs add no points. The separate `GET /api/internal/surprising-saturday/score/:uuid` endpoint remains available. Killshift tells all players when the official top three change. These messages show player colors, role labels, and colored places. Committee members do not count in the official top three. The mod also reads player presentation data when a player joins. The mod works without these variables, but it does not send event data.
