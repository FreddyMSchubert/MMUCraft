# Mob behavior

This document describes the behavior in Killshift 0.1.0 for Minecraft 26.3.

## Behavior that applies to every mob

All listed mobs can become forms, including giants. A player kill copies the victim's form and appearance. If the victim has no mob form, the killer gets a player form with the dead player's skin. `/shift` with your own name returns you to normal; shifting to another player uses that player's skin as a mannequin. Killshift saves the source appearance. It copies the source attribute values for health, armor, attack, knockback, fall, flight, jump, safe fall distance, and step height. It converts land mob movement speed for player controls. The player camera matches the form's actual eye height. The owner sees the display at 50% scale. Other players see the full-size display. Attack damage never falls below one.

Each new sneak press plays the form's ambient sound. Holding sneak plays it once. The display entity follows the player's position and rotation. It keeps the source entity's equipment, cube size, and baby age. Aquatic forms can sprint and swim in water; other mob forms cannot use player sprint or swim movement. A player returns to normal after death. A form and its visual data stay active after a player rejoins. On a form change, the old display stays in the world with the player's health and effects. Its mob AI resumes. A killed mob drops no items or experience when the kill causes a shift. The killer moves to the dead entity's position.

Mobs do not target a player with the same exact form. Undead mobs do not target undead forms. Spiders and cave spiders do not target either spider form. Creepers do not target skeleton forms. Iron golems target hostile monster forms, except creepers. Wild wolves target sheep forms. Foxes target chicken forms and do not flee from them.

All forms can eat beetroot and beetroot soup. Other foods follow the form's natural diet. Panda forms eat bamboo. Animal forms use the game's food check. Player forms use normal player food rules. A rejected food use shows the attempted food and the full allowed-food list.

## Shared trait groups

| Trait | Forms | Behavior |
| --- | --- | --- |
| Forced flight | Allay, bat, bee, blaze, ender dragon, ghast, happy ghast, parrot, phantom, vex, wither | Player flight stays active. |
| Aquatic movement | Axolotl, cod, dolphin, drowned, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, turtle, zombie nautilus | Water movement efficiency is moderate. Sprint and swim controls work in water. Fish and other water-only forms have land movement suppressed, and their displays sit lower than the player. |
| Water breathing | Axolotl, cod, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, zombie nautilus | Air stays full underwater. Air falls on land. The player takes drowning damage after the air supply ends. |
| Sun-sensitive forms | Types in the game's `burn_in_daylight` tag | Sunlight ignites the player when the head slot is empty. |
| Wall climbing | Spider, cave spider | Movement input near a wall gives a steady upward climb. |
| Bounce movement | Slime, magma cube, sulfur cube | Ground movement has no horizontal speed. Air movement works, so the player must jump to travel. |
| Lava-safe | Blaze, magma cube, strider, wither, wither skeleton | Fire is cleared. The form receives fire resistance. |
| Jump and movement | Every form | Killshift copies jump strength and converts mob movement speed for player controls. Water-only forms can make a small horizontal nudge while jumping on land. |
| No fall damage | Cat, chicken, and all forced-flight forms | Fall damage multiplier is zero. Chicken descent is also limited to a slow speed. |
| Screen effect | Creeper, spider, cave spider, enderman, endermite, shulker | The server applies the creeper, spider, or invert post effect. It removes its effect when the form ends. |
| Iron golem water movement | Iron golem | The form sinks in water, moves slowly, and keeps its air supply. |

## Active and reactive behavior

The ninth hotbar slot holds a marked item that matches the active ability. For example, a creeper holds TNT. Right-click with it while aiming at air, a block, or an entity. The slot holds a marked barrier for all other forms, including the normal player. The item stays in that slot.

| Form | Behavior |
| --- | --- |
| Bee | Damage makes the bee angry for 10 seconds. Anger increases flight speed. A melee hit poisons the target. Right-click a flower with the marked honeycomb to heal. |
| Blaze | Right-click shoots a small fireball. |
| Bogged | A bow receives a replacement arrow when no arrow remains. |
| Breeze | Right-click shoots a wind charge. |
| Cat | Nearby creepers lose their target and move away. |
| Cave spider | A melee hit poisons the target. The form can climb walls and ceilings. |
| Creaking | The player cannot move horizontally while another player looks directly at the form. |
| Creeper | Right-click with the marked TNT to explode and die. A charged creeper has twice the blast radius. |
| Elder guardian | A melee hit gives mining fatigue to the target. Right-click charges a stronger laser on a living target in sight. |
| Ender dragon | Right-click shoots a dragon fireball. |
| Enderman | Right-click with the marked ender pearl to throw a pearl. Water and rain cause damage. Direct observation gives a large speed increase. |
| Evoker | Right-click raises a line of evoker fangs. |
| Ghast | Right-click shoots an explosive fireball. |
| Glow squid | Right-click releases squid ink particles. |
| Guardian | A melee hit gives mining fatigue to the target. Right-click charges a laser on a living target in sight, then damages the target. |
| Iron golem | The form sinks in water and cannot use player swim movement. |
| Llama | Right-click spits in the look direction. |
| Mooshroom | Right-click restores six hunger points and adds a bowl. |
| Parched | A bow receives a replacement arrow when no arrow remains. |
| Shulker | Right-click searches for a safe random position and teleports there. |
| Silverfish | Right-click an infestable block to remove it and summon an allied silverfish. |
| Skeleton | A bow receives a replacement arrow when no arrow remains. |
| Sniffer | Right-click on a grass block finds torchflower seeds or a pitcher pod. |
| Squid | Right-click releases squid ink particles. Damage gives a short water speed boost. |
| Glow squid | Right-click releases squid ink particles. Damage gives a short water speed boost. |
| Stray | A bow receives a replacement arrow when no arrow remains. |
| Trader llama | Right-click spits in the look direction. |
| Vex | The server gives the player no-physics movement and forced flight. The vanilla client can still block movement through walls. |
| Warden | Right-click fires a sonic boom in the look direction, with or without an entity target. |
| Witch | Right-click throws a random poison, slowness, weakness, or harming splash potion. |
| Wither | Right-click shoots a wither skull. A melee hit gives Wither to the target. |
| Wither skeleton | A melee hit gives Wither to the target. |

## Full mob coverage

The following table lists the supported forms in this build. `Common` means that the form has the shared attributes, scale, display, sound, food, and faction behavior. A shared trait or active behavior adds to that behavior.

| Mob | Added behavior |
| --- | --- |
| Allay | Forced flight and no fall damage |
| Armadillo | Common |
| Axolotl | Aquatic movement and water breathing |
| Bat | Forced flight and no fall damage |
| Bee | Forced flight, no land speed, anger flight speed, poison hit, flower healing, and no fall damage |
| Blaze | Forced flight, lava safety, fireball, and no fall damage |
| Bogged | Sunlight rule and arrow refill |
| Breeze | Wind charge |
| Camel | Source jump strength |
| Camel husk | Source jump strength |
| Cat | Creeper repulsion and no fall damage |
| Cave spider | Climbing, poison hit, and spider post effect |
| Chicken | Slow falling, no fall damage, and fox pursuit |
| Cod | Aquatic movement and water breathing |
| Copper golem | Common |
| Cow | Common |
| Creaking | Freeze while watched |
| Creeper | Explosion (larger while charged), skeleton peace, and creeper post effect |
| Dolphin | Aquatic movement |
| Donkey | Source jump strength |
| Drowned | Aquatic movement and sunlight rule |
| Elder guardian | Aquatic movement, water breathing, mining-fatigue hit, and stronger laser |
| Enderman | Pearl, wet damage, watched speed, and invert post effect |
| Endermite | Invert post effect |
| Ender dragon | Forced flight, dragon fireball, and no fall damage |
| Evoker | Fang attack |
| Fox | Common |
| Frog | Common |
| Ghast | Forced flight, explosive fireball, and no fall damage |
| Giant | Zombie-like movement and combat with giant health and size |
| Happy ghast | Forced flight and no fall damage |
| Glow squid | Aquatic movement, water breathing, ink, and damage speed boost |
| Goat | Source jump strength |
| Guardian | Aquatic movement, water breathing, mining-fatigue hit, and laser |
| Hoglin | Common |
| Horse | Source jump strength |
| Husk | Common |
| Illusioner | Common |
| Iron golem | Sinks in water and keeps air supply |
| Llama | Spit |
| Magma cube | Bounce movement and lava safety |
| Mooshroom | Stew hunger restoration |
| Mule | Source jump strength |
| Nautilus | Aquatic movement and water breathing |
| Ocelot | Common |
| Panda | Bamboo diet |
| Parched | Arrow refill |
| Parrot | Forced flight and no fall damage |
| Phantom | Forced flight, sunlight rule, and no fall damage |
| Pig | Common |
| Piglin | Common |
| Piglin brute | Common |
| Pillager | Common |
| Polar bear | Common |
| Pufferfish | Aquatic movement, water breathing, and poison hit |
| Rabbit | Source jump strength |
| Ravager | Common |
| Salmon | Aquatic movement and water breathing |
| Sheep | Wild wolves pursue this form |
| Shulker | Random teleport and invert post effect |
| Silverfish | Infest a block to summon an ally |
| Skeleton | Sunlight rule, arrow refill, and creeper peace |
| Skeleton horse | Source jump strength |
| Slime | Bounce movement |
| Sniffer | Ancient seed digging |
| Snow golem | Common |
| Spider | Climbing and spider post effect |
| Squid | Aquatic movement, water breathing, ink, and damage speed boost |
| Stray | Sunlight rule and arrow refill |
| Strider | Lava safety |
| Sulfur cube | Bounce movement |
| Tadpole | Aquatic movement and water breathing |
| Trader llama | Spit |
| Tropical fish | Aquatic movement and water breathing |
| Turtle | Aquatic movement |
| Vex | Forced flight, server no physics, and no fall damage |
| Villager | Common |
| Vindicator | Common |
| Wandering trader | Common |
| Warden | Sonic boom |
| Witch | Random harmful splash potion |
| Wither | Forced flight, lava safety, wither skull, Wither hit, and no fall damage |
| Wither skeleton | Lava safety and Wither hit |
| Wolf | Common |
| Zoglin | Common |
| Zombie | Sunlight rule |
| Zombie horse | Sunlight rule and source jump strength |
| Zombie nautilus | Aquatic movement, water breathing, and sunlight rule |
| Zombie villager | Sunlight rule |
| Zombified piglin | Common |
| Player | Dead player's skin on a mannequin if the victim has no mob form; normal player food rules |
