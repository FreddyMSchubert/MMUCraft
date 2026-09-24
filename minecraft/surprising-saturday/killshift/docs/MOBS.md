# Mob behavior

This document describes the behavior in Killshift 0.1.0 for Minecraft 26.3.

## Behavior that applies to every mob

Every mob can become a form. Killshift copies the source mob appearance into the display entity when possible. It copies the mob health, armor, eye height, and attack damage. The player scale follows the mob eye height. The owner's display is smaller, while other players see the full-size mob. Attack damage never falls below one. Most forms use normal player speed on land.

Sneak to play the form's ambient sound. The display entity copies the player pose, rotation, sprint state, swim state, and held equipment. A player returns to normal after death or after killing a player.

Mobs do not target a player with the same exact form. Undead mobs do not target undead forms. Spiders and cave spiders do not target either spider form. Creepers do not target skeleton forms.

## Shared trait groups

| Trait | Forms | Behavior |
| --- | --- | --- |
| Forced flight | Allay, bat, bee, blaze, ender dragon, ghast, happy ghast, parrot, phantom, vex, wither | Creative flight stays enabled. Forms that cannot walk stay in flight. |
| Aquatic movement | Axolotl, cod, dolphin, drowned, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, turtle, zombie nautilus | Water movement efficiency is full. Land speed stays at normal player speed. |
| Water breathing | Axolotl, cod, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, zombie nautilus | Air stays full underwater. Air falls on land. The player takes drowning damage after the air supply ends. |
| Sun-sensitive undead | Bogged, drowned, husk, parched, phantom, skeleton, skeleton horse, stray, wither, wither skeleton, zoglin, zombie, zombie horse, zombie nautilus, zombie villager, zombified piglin | Sunlight ignites the player when the head slot is empty. |
| Wall climbing | Spider, cave spider | Horizontal contact climbs a wall. Jump under a ceiling holds the player against it. |
| Bounce movement | Slime, magma cube, sulfur cube | Ground movement has no horizontal speed. Air movement works, so the player must jump to travel. |
| Lava-safe | Blaze, magma cube, strider, wither, wither skeleton | Fire is cleared. The form receives fire resistance. |
| Strong jump | Camel, camel husk, donkey, goat, horse, mule, rabbit, skeleton horse, zombie horse | Jump strength is 1.75 times the player value. |
| Quick movement | Cat, cave spider, enderman, fox, ocelot, rabbit, spider, vex, wolf | Land movement is 1.2 times normal player speed. |
| No fall damage | Cat and all forced-flight forms | Safe fall distance is increased. |

## Active and reactive behavior

Use an empty hand to activate a right-click ability.

| Form | Behavior |
| --- | --- |
| Bee | Damage makes the bee angry for 10 seconds. Anger increases flight and land speed. A melee hit poisons the target. |
| Blaze | Right-click shoots a small fireball. |
| Bogged | A bow receives a replacement arrow when no arrow remains. |
| Breeze | Right-click shoots a wind charge. |
| Cat | Nearby creepers lose their target and move away. |
| Cave spider | A melee hit poisons the target. The form can climb walls and ceilings. |
| Creaking | The player cannot move horizontally while another player looks directly at the form. |
| Creeper | Right-click creates a mob explosion. A player killed by this explosion still becomes the next form. |
| Elder guardian | A melee hit gives mining fatigue to the target. |
| Ender dragon | Right-click shoots a dragon fireball. |
| Enderman | Right-click throws an ender pearl without an item. Water and rain cause damage. Direct observation gives a large speed increase. |
| Ghast | Right-click shoots an explosive fireball. |
| Glow squid | Right-click releases squid ink particles. |
| Guardian | A melee hit gives mining fatigue to the target. |
| Llama | Right-click spits in the look direction. |
| Mooshroom | Right-click restores six hunger points and adds a bowl. |
| Parched | A bow receives a replacement arrow when no arrow remains. |
| Shulker | Right-click teleports to a random valid position. |
| Skeleton | A bow receives a replacement arrow when no arrow remains. |
| Sniffer | Right-click on a grass block finds torchflower seeds or a pitcher pod. |
| Squid | Right-click releases squid ink particles. |
| Stray | A bow receives a replacement arrow when no arrow remains. |
| Trader llama | Right-click spits in the look direction. |
| Vex | The player receives no-physics movement and forced flight. |
| Warden | Right-click fires a sonic boom at the entity in the look direction. |
| Witch | Right-click throws a poison splash potion. |
| Wither | Right-click shoots a wither skull. A melee hit gives Wither to the target. |
| Wither skeleton | A melee hit gives Wither to the target. |

## Full mob coverage

The following table lists every living mob type in Minecraft 26.3. `Common` means that the form has the shared health, armor, size, damage, display, sound, and faction behavior. A shared trait or active behavior adds to that common behavior.

| Mob | Added behavior |
| --- | --- |
| Allay | Forced flight and no fall damage |
| Armadillo | Common |
| Axolotl | Aquatic movement and water breathing |
| Bat | Forced flight and no fall damage |
| Bee | Forced flight, anger speed, poison hit, and no fall damage |
| Blaze | Forced flight, lava safety, fireball, and no fall damage |
| Bogged | Undead sunlight rule and arrow refill |
| Breeze | Wind charge |
| Camel | Strong jump |
| Camel husk | Strong jump |
| Cat | Quick movement, creeper repulsion, and no fall damage |
| Cave spider | Quick movement, climbing, poison hit |
| Chicken | Common |
| Cod | Aquatic movement and water breathing |
| Copper golem | Common |
| Cow | Common |
| Creaking | Freeze while watched |
| Creeper | Explosion and skeleton peace |
| Dolphin | Aquatic movement |
| Donkey | Strong jump |
| Drowned | Aquatic movement and undead sunlight rule |
| Elder guardian | Aquatic movement, water breathing, and mining-fatigue hit |
| Enderman | Quick movement, pearl, wet damage, and watched speed |
| Endermite | Common |
| Ender dragon | Forced flight, dragon fireball, and no fall damage |
| Evoker | Common |
| Fox | Quick movement |
| Frog | Common |
| Ghast | Forced flight, explosive fireball, and no fall damage |
| Happy ghast | Forced flight and no fall damage |
| Giant | Common |
| Glow squid | Aquatic movement, water breathing, and ink |
| Goat | Strong jump |
| Guardian | Aquatic movement, water breathing, and mining-fatigue hit |
| Hoglin | Common |
| Horse | Strong jump |
| Husk | Undead sunlight rule |
| Illusioner | Common |
| Iron golem | Common |
| Llama | Spit |
| Magma cube | Bounce movement and lava safety |
| Mooshroom | Stew hunger restoration |
| Mule | Strong jump |
| Nautilus | Aquatic movement and water breathing |
| Ocelot | Quick movement |
| Panda | Common |
| Parched | Undead sunlight rule and arrow refill |
| Parrot | Forced flight and no fall damage |
| Phantom | Forced flight, undead sunlight rule, and no fall damage |
| Pig | Common |
| Piglin | Common |
| Piglin brute | Common |
| Pillager | Common |
| Polar bear | Common |
| Pufferfish | Aquatic movement and water breathing |
| Rabbit | Quick movement and strong jump |
| Ravager | Common |
| Salmon | Aquatic movement and water breathing |
| Sheep | Common |
| Shulker | Random teleport |
| Silverfish | Common |
| Skeleton | Undead sunlight rule, arrow refill, and creeper peace |
| Skeleton horse | Undead sunlight rule and strong jump |
| Slime | Bounce movement |
| Sniffer | Ancient seed digging |
| Snow golem | Common |
| Spider | Quick movement and climbing |
| Squid | Aquatic movement, water breathing, and ink |
| Stray | Undead sunlight rule and arrow refill |
| Strider | Lava safety |
| Sulfur cube | Bounce movement |
| Tadpole | Aquatic movement and water breathing |
| Trader llama | Spit |
| Tropical fish | Aquatic movement and water breathing |
| Turtle | Aquatic movement |
| Vex | Forced flight, no physics, quick movement, and no fall damage |
| Villager | Common |
| Vindicator | Common |
| Wandering trader | Common |
| Warden | Sonic boom |
| Witch | Poison splash potion |
| Wither | Forced flight, lava safety, wither skull, Wither hit, undead sunlight rule, and no fall damage |
| Wither skeleton | Lava safety, Wither hit, and undead sunlight rule |
| Wolf | Quick movement |
| Zoglin | Undead sunlight rule |
| Zombie | Undead sunlight rule |
| Zombie horse | Undead sunlight rule and strong jump |
| Zombie nautilus | Aquatic movement, water breathing, and undead sunlight rule |
| Zombie villager | Undead sunlight rule |
| Zombified piglin | Undead sunlight rule |
