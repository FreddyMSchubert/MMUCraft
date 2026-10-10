# Mob behavior

This document describes the behavior in Killshift 0.1.0 for Minecraft 26.3.

## Behavior that applies to every mob

All listed mobs can become forms, including giants. A player kill copies the victim's form and appearance. If the victim has no mob form, the killer gets a player form with the dead player's skin. `/shift` with your own name returns you to normal; shifting to another player uses that player's skin as a mannequin. Killshift saves the source appearance. It copies the source attribute values for health, armor, attack, knockback, fall, flight, jump, safe fall distance, and step height. Horse-family forms keep the killed animal's exact movement speed and jump strength; other land mob movement speed is converted for player controls. The player camera matches the form's actual eye height. The owner sees the display at up to 50% scale, reduced further when its height could cover the camera. Ghasts and happy ghasts are capped at 12.5% for their owner; silverfish are capped at 20%. Other players see the full-size display. Attack damage never falls below one.

Each new sneak press plays the form's ambient sound. Holding sneak plays it once. The display entity follows the player's position and rotation. It keeps the source entity's equipment, cube size, and baby age. It clears inherited resting, sleeping, sitting, curled, inflated, and wall-attached poses so moving forms look awake. It clears stale anger, nectar, stung, and lit-fuse states; striders change their warm or cold appearance with their surroundings. Aquatic forms can sprint and swim in water; camel forms can sprint on land. Other mob forms cannot use player sprint or swim movement. A zombie form becomes a drowned form when it starts drowning, without killing the player. A player returns to normal after death. A form and its visual data stay active after a player rejoins. On a form change, the old display stays in the world with the player's health and effects. Its mob AI resumes. A player-killed mob gives its normal non-equipment item drops directly to the killer's inventory, transfers every equipped item once, and gives its experience directly to the killer. Vanilla equipment drops are suppressed. If the inventory is full, items wait until space is available. A form left behind by a shift gives no further drops or experience when killed; another mob of the same type still gives its own drops. The killed mob has no lingering death animation or death particles. The killer moves to the dead entity's position.

Mobs do not target a player with the same exact form. Hostile monsters do not target hostile monster forms. Undead mobs also leave undead forms alone outside that category. Iron golems target hostile monster forms, except creepers. Wild wolves target sheep forms. Foxes target chicken forms and do not flee from them.

All forms can eat beetroot and beetroot soup. Other foods follow the form's natural diet. Taming foods count as food, including seeds for parrots and bones for wolves. A cookie kills a parrot form. Panda forms eat bamboo. Animal forms use the game's food check. Player forms use normal player food rules. A rejected food use shows the attempted food and the full allowed-food list.

## Shared trait groups

| Trait | Forms | Behavior |
| --- | --- | --- |
| Forced flight | Allay, bat, bee, blaze, ender dragon, ghast, happy ghast, parrot, phantom, vex, wither | Player flight stays active. |
| Aquatic movement | Axolotl, cod, dolphin, drowned, elder guardian, glow squid, guardian, happy ghast, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, turtle, zombie nautilus | Water movement efficiency is moderate. Sprint and swim controls work in water. Fish and other water-only forms have land movement suppressed. Squid and salmon displays sit lower in water and appear at up to 30% scale to their owner. Other water-only displays are lowered for their owner when they block the camera. Other players see full-size displays. |
| Water breathing | Axolotl, cod, drowned, elder guardian, glow squid, guardian, happy ghast, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, turtle, zombie nautilus | Air stays full underwater. Drowned, guardians, happy ghasts, and turtles also keep full air on land. Other forms use their mob's maximum air supply on land and take drowning damage after it runs out. Rain rehydrates axolotls. |
| Sun-sensitive forms | Types in the game's `burn_in_daylight` tag | Sunlight ignites the player when the head slot is empty and the player is dry. Water or rain extinguishes the form. |
| Wall climbing | Spider, cave spider | Movement input near a wall gives a steady upward climb. |
| Bounce movement | Slime, magma cube, sulfur cube | Ground movement has no horizontal speed. Air movement works, so the player must jump to travel. Jump strength is 0.9 and fall damage is disabled. |
| Lava-safe | Blaze, magma cube, strider, wither, wither skeleton | Fire is cleared. The form receives fire resistance. |
| Jump and movement | Every form | Killshift copies jump strength. Horse-family forms also keep the source movement speed; other land mob speed is converted for player controls. Breezes move at 0.08 speed and have 0.8 jump strength; they cannot sprint. Camel and camel husk forms can sprint. Water-only forms can make a small horizontal nudge while jumping on land. |
| No fall damage | All forced-flight forms and types in the game's `fall_damage_immune` tag | Fall damage multiplier is zero. Chicken descent is also limited to a slow speed. |
| Powder snow walking | Types in the game's `powder_snow_walkable_mobs` tag | The form stands on powder snow without leather boots. |
| Water sensitivity | Enderman, blaze, strider, snow golem | Water and rain deal damage. |
| Screen effect | Creeper, spider, cave spider, enderman, endermite, shulker | The server applies the creeper, spider, or invert post effect. It removes its effect when the form ends. |
| Iron golem water movement | Iron golem | The form sinks in water, moves slowly, and keeps its air supply. |

## Active and reactive behavior

The ninth hotbar slot holds an inert item that displays the ability's icon and explains what right-click does. It never performs the icon item's normal action or gets consumed. For forms without an ability, including the normal player, the same inert item displays a barrier icon. Its cooldown overlay shows the remaining ability wait. Projectiles start at the player's eye and follow the view direction.

| Form | Behavior |
| --- | --- |
| Bee | Damage makes the bee angry for 10 seconds. Anger increases flight speed. A melee hit poisons the target. Right-click a flower with the marked honeycomb to heal. |
| Blaze | Right-click shoots a small fireball. Water and rain damage the form, snowballs deal three damage, and underwater flight slows down. |
| Bogged | A bow shoots unlimited poison arrows. |
| Breeze | Right-click shoots a wind charge. The form moves slowly, jumps high, cannot sprint, and is friendly to creeper forms. |
| Camel, camel husk | Right-click on the ground to dash forward and upward. The dash uses the form's movement speed and jump strength and has a 55-tick cooldown. |
| Cat | Nearby creepers lose their target and move away. |
| Cave spider | A melee hit poisons the target. The form can climb walls and ceilings. |
| Creaking | The player takes no damage. Looking at the form freezes walking, sprinting, crouching, and jumping, whether the observer is a player or a mob. |
| Creeper | Right-click to explode and die. The explosion radius is 4.5 blocks, or 9 blocks when charged. Its own death and display give no kill credit; other victims can. |
| Elder guardian | A melee hit gives mining fatigue to the target. Right-click charges a stronger laser on a living target in sight. |
| Ender dragon | Right-click shoots a dragon fireball. |
| Enderman | Right-click to throw an unlimited ender pearl. Water and rain cause damage. Direct observation gives a large speed increase. |
| Evoker | Right-click raises a line of evoker fangs. |
| Ghast | Right-click shoots an explosive fireball. |
| Glow squid | Right-click releases squid ink particles. |
| Guardian | A melee hit gives mining fatigue to the target. Right-click charges a laser on a living target in sight, then damages the target. |
| Iron golem | The form sinks in water and cannot use player swim movement. |
| Llama | Right-click spits in the look direction. |
| Mooshroom | Right-click restores six hunger points and adds a bowl. |
| Parched | A bow shoots unlimited weakness arrows. |
| Shulker | Each new jump press attempts a safe random teleport. Right-click shoots a levitation bullet that homes toward a living target in sight or fires in the look direction without homing when there is none. Bullets do not target or hit shulker-transformed players. |
| Silverfish | Right-click an infestable block to remove it and summon an allied silverfish. |
| Skeleton | A bow shoots unlimited normal arrows. |
| Sniffer | Right-click on a grass block finds torchflower seeds or a pitcher pod. |
| Snow golem | Right-click throws unlimited snowballs. Water and rain damage the form. |
| Squid | Right-click releases squid ink particles. Damage gives a short water speed boost. |
| Glow squid | Right-click releases squid ink particles. Damage gives a short water speed boost. |
| Stray | A bow shoots unlimited slowness arrows. |
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
| Blaze | Forced flight, lava safety, fireball, wet and snowball damage, and no fall damage |
| Bogged | Sunlight rule and unlimited poison arrows |
| Breeze | Wind charge, slow movement, high jump, and creeper friendship |
| Camel | Source jump strength and forward dash |
| Camel husk | Source jump strength and forward dash |
| Cat | Creeper repulsion and no fall damage |
| Cave spider | Climbing, poison hit, and spider post effect |
| Chicken | Slow falling, no fall damage, and fox pursuit |
| Cod | Aquatic movement and water breathing |
| Copper golem | Common |
| Cow | Common |
| Creaking | No damage and freeze while watched by players or mobs |
| Creeper | Explosion (larger while charged), skeleton peace, and creeper post effect |
| Dolphin | Aquatic movement |
| Donkey | Source jump strength |
| Drowned | Aquatic movement, breathing on land and underwater, and sunlight rule |
| Elder guardian | Aquatic movement, full air supply on land and underwater, mining-fatigue hit, and stronger laser |
| Enderman | Pearl, wet damage, watched speed, and invert post effect |
| Endermite | Invert post effect |
| Ender dragon | Forced flight, dragon fireball, and no fall damage |
| Evoker | Fang attack |
| Fox | Common |
| Frog | Common |
| Ghast | Forced flight, explosive fireball, and no fall damage |
| Giant | Zombie-like movement and combat with giant health and size |
| Happy ghast | Forced flight, aquatic movement, full air supply underwater, and no fall damage |
| Glow squid | Aquatic movement, water breathing, ink, and damage speed boost |
| Goat | Source jump strength |
| Guardian | Aquatic movement, full air supply on land and underwater, mining-fatigue hit, and laser |
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
| Parched | Unlimited weakness arrows |
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
| Shulker | Jump teleport, homing levitation bullet, and invert post effect |
| Silverfish | Infest a block to summon an ally |
| Skeleton | Sunlight rule, unlimited arrows, and creeper peace |
| Skeleton horse | Source jump strength |
| Slime | Bounce movement |
| Sniffer | Ancient seed digging |
| Snow golem | Unlimited snowballs and water or rain damage |
| Spider | Climbing and spider post effect |
| Squid | Aquatic movement, water breathing, ink, and damage speed boost |
| Stray | Sunlight rule and unlimited slowness arrows |
| Strider | Lava safety |
| Sulfur cube | Bounce movement |
| Tadpole | Aquatic movement and water breathing |
| Trader llama | Spit |
| Tropical fish | Aquatic movement and water breathing |
| Turtle | Aquatic movement and full air supply |
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
