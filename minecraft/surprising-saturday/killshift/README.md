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
- Mining a creaking heart turns the player into a creaking and records a creaking completion for event score.
- The killer moves to the dead entity's position.
- A transformed player returns to normal on death.
- `/freeze` keeps your current form when you kill a mob or player and prevents those kills from earning event points. `/unfreeze` resumes shifting and scoring. Death also unfreezes you. Freeze stays active across reconnects.
- Freeze does not change kill rewards. Mining a creaking heart does not shift or score while frozen.
- A player kill gives the killer a player form with the dead player's skin.
- If the dead player has a mob form, the killer gets that mob form and its appearance.
- A form stays active when the player leaves and rejoins.
- A form keeps its health, attributes, and saved visual data after the End exit creates a new player object.
- On a form change, the old display stays in the world. It receives the player's health and effects, and its mob AI resumes.
- A mob killed for a shift gives its normal non-equipment drops and experience directly to the killer, plus all of its equipped items once. Drops wait for inventory space if necessary. A previously possessed mob gives no more rewards, while another mob of the same type does.
- A zombie form turns into a drowned form on drowning damage without killing the player.
- Each new creeper has a 5% chance to be charged. Each spawned zombie has a 1% chance to become a giant.
- Giants use zombie pursuit and target goals. They keep giant health, speed, and attack damage.
- The display keeps the source mob's equipment appearance. The source's equipped items transfer to the killer once, regardless of their normal drop chances; vanilla equipment drops are suppressed.
- Every form has at least one point of attack damage.
- Each new sneak press plays the form's ambient sound. Holding sneak plays it once.
- A creeper ability explodes with a 4.5-block radius. A charged creeper uses a 9-block radius.
- A player respawns at a random safe surface spot in a ring around the death location in the same dimension. Members and Committee spawn 50–100 blocks away; other players spawn 100–200 blocks away.

## Movement and combat

Killshift reads the killed entity's current attribute values. These include health, armor, damage, knockback, gravity, jump strength, movement speed, safe fall distance, and step height. Horse-family forms use the killed animal's exact movement speed and jump strength. It converts other land mob movement speed for player controls. The player's scale places the camera at the form's actual eye height. It also changes the player hitbox.

Aquatic forms have moderate water movement efficiency and can sprint and swim in water. Happy ghasts also have aquatic movement and keep full air supply underwater. Camel and camel husk forms can sprint on land. Fish and squid use player-scale movement speed instead of the much higher default mob speed. Fish and bees have their land movement suppressed. Water-only forms can jump on land but gain little horizontal motion while airborne. Flying forms use player flight controls. Killshift forces flight each tick for these forms, including allays and happy ghasts. Chicken forms fall slowly. Other mob forms cannot use player sprint or swim movement. Iron golems sink in water and keep their air supply. Drowned forms breathe on land and underwater. Water-only forms use their mob's maximum air supply on land, including the axolotl's longer timer. Blaze, strider, snow golem, and enderman forms take damage in water and rain. Forms in Minecraft's fall-damage-immune and powder-snow-walkable mob tags inherit those rules. Creaking forms take no damage. When a player or mob looks directly at a creaking form, the player cannot walk, sprint, crouch, or jump.

Turtles and guardians keep full air supply on land and underwater. Sun-sensitive forms stop burning when submerged. Blaze forms slow down underwater and take three damage from snowballs. Breezes have slow ground speed and a high jump and cannot sprint. Slime, magma cube, and sulfur cube forms jump high and take no fall damage. Hostile monsters leave other hostile monster forms alone. The ninth-slot item always has an inert base, even when it displays a barrier or block icon. It never consumes the displayed pearl, snowball, wind charge, or totem. Ability projectiles launch from the player's eye in the view direction. Skeleton bows supply unlimited normal arrows; bogged, stray, and parched bows supply unlimited poison, slowness, and weakness arrows respectively.

Killshift keeps attack damage at one or more. A weak form can therefore kill another mob and change form again.

Every form can eat beetroot and beetroot soup. Taming foods count as food, including parrot seeds and wolf bones. A cookie kills a parrot form. If a player tries to eat another forbidden food, Killshift names the food and lists the form's allowed foods.

## Visual disguise

Killshift makes the real player invisible. Its presentation team does not reveal invisible teammates. It creates a silent, invulnerable, no-AI copy of the killed mob. The copy has no physics and follows the player. A no-collision team stops the copy from pushing players on the server and on clients. The form retains the source entity's saved visual data, such as its variant.

Other players see the full-size copy at the player's position. The owner sees most forms at up to 50% scale; squid and salmon appear at up to 30% scale, silverfish at up to 20%, and ghasts at up to 12.5%. The owner's view shrinks further according to the mob's height and the current camera height, including while crouching. Squid and salmon displays sit lower in water, while the owner sees a sniffer display behind the camera. Squid and salmon copies keep their tentacle and tail animations but cannot swim away under their own movement. On land they follow a small flop animation. The player's eye height still follows the form, but small forms retain enough collision width to keep the camera out of walls. Killshift keeps the source cube size and baby age. Dragon copies face the player's look direction, use a steady wing pace, and start in a flying phase without the killed dragon's death particles. Killshift moves the copy each server tick. Minecraft tracks and interpolates its position for clients. The server does not receive the client's camera mode.

Attacks against another player's copy are redirected to its owner. Projectile hits on the copy also strike the owner, including each projectile's normal hit effects. Projectiles skip their shooter's own copy. When the owner clicks their own copy, Killshift aims past it to the next entity or block. Right-clicks can use a block behind the copy, such as a block used to build a tower. Attack clicks can hit an entity behind the copy. Held block mining still needs the vanilla client to aim at the block.

Killshift does not save active display entities with the world. It removes old orphan displays when their chunks load. This prevents stationary no-AI copies after a restart.

The locator bar shows players in the same dimension at any distance. Sneaking, invisibility, equipment, spectator mode, and transformation do not hide their icons.

The ninth hotbar slot always contains a marked inert item. Each active ability displays a matching icon, such as TNT for creepers and an ender pearl for endermen. Other forms display a barrier. Right-click with the marked item while aiming at air, a block, or an entity. The item stays in that slot. Its cooldown overlay shows when the ability is ready. A creeper dies after its explosion.

Snow golems throw snowballs with their ability item. Camels and camel husks dash forward and upward from the ground. Shulkers teleport on each new jump press and shoot levitation bullets with their ability item; the bullets ignore shulker-transformed players.

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
- `MobMixin` stops hostile monsters from targeting hostile player forms and preserves other shared-family alliances.

This split keeps entity display code out of movement code. It also keeps temporary attributes in one lifecycle. A reset can therefore remove every Killshift modifier without changing unrelated modifiers from other mods.

See [docs/MOBS.md](docs/MOBS.md) for the complete behavior list.

## Current limits

- Some abilities need a target or valid terrain. The guardian beam needs a living target in sight, camel dashes need ground contact, and shulker jump teleports need a safe destination. Shulker bullets home toward a target in sight or fire in the look direction without homing when there is none.
- Vex movement uses server no-physics mode. Vanilla client movement can still stop the player at a wall. Full spectator movement also grants other spectator powers.
- Powder-snow walking is enforced by the server. A vanilla client still predicts player collision, so a form without leather boots may briefly dip before the server corrects it.
- The display entity follows the player each tick. Network interpolation can still cause visual delay.
- Some mobs have only the common form behavior. The mob list marks these cases.
- If no safe location exists in the allowed respawn ring, Minecraft keeps its normal respawn position.

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

## Discord channel

Set `DISCORD_EVENT_CHANNEL_ID` and `DISCORD_EVENT_WEBHOOK_URL` in the root `.env`. Create the webhook in that event text channel. Killshift uses the existing `DISCORD_BOT_TOKEN` to read messages from that channel and its own webhook to post Minecraft chat, joins, leaves, deaths, accepted event scores, and podium changes. Discord messages and attachment links appear in Killshift chat. They do not go to the main or waiting room server. The bot needs View Channel and Read Message History in the event channel, and Message Content Intent must be enabled. Killshift reads Discord directly; the API only supplies player profile data, scores, and optional avatar images. Production builds the avatar URL from `PUBLIC_URL`. For a local instance with a public API URL, set `DISCORD_AVATAR_BASE_URL` to that URL plus `/api/discord/avatar`.

If Minecraft posts to Discord but Discord messages do not reach Minecraft, check both Read Message History in the event channel and Message Content Intent under the bot application's Developer Portal settings. Discord can return no messages when history access is missing, or messages with empty content when the intent is missing. Killshift logs these cases. Send a new human message after changing settings; it does not replay messages that predate its first successful poll.
