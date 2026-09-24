# Mob behavior

This document describes the behavior in Killshift 0.1.0 for Minecraft 26.3.

## Behavior that applies to every mob

All listed mobs except the giant can become forms. A player kill gives the killer a player form with the dead player's skin. Killshift saves the source appearance. It copies the source attribute values for health, armor, attack, knockback, fall, flight, jump, movement, safe fall distance, and step height. The player scale follows the source eye height. The owner's display is smaller. Other players see the full-size display. Attack damage never falls below one.

Sneak to play the form's ambient sound. The display entity copies the player pose, rotation, sprint state, swim state, and held equipment. A player returns to normal after death. A form and its visual data stay active after a player rejoins.

Mobs do not target a player with the same exact form. Undead mobs do not target undead forms. Spiders and cave spiders do not target either spider form. Creepers do not target skeleton forms. Iron golems target hostile monster forms, except creepers.

Forms can eat only food in their natural diet. Forms with no natural food cannot eat. Panda forms eat bamboo. Animal forms use the game's food check. Player forms use normal player food rules.

## Shared trait groups

| Trait | Forms | Behavior |
| --- | --- | --- |
| Forced flight | Allay, bat, bee, blaze, ender dragon, ghast, happy ghast, parrot, phantom, vex, wither | Player flight stays active. |
| Aquatic movement | Axolotl, cod, dolphin, drowned, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, turtle, zombie nautilus | Water movement efficiency is full. Fish and other water-only forms have no land movement speed. |
| Water breathing | Axolotl, cod, elder guardian, glow squid, guardian, nautilus, pufferfish, salmon, squid, tadpole, tropical fish, zombie nautilus | Air stays full underwater. Air falls on land. The player takes drowning damage after the air supply ends. |
| Sun-sensitive forms | Types in the game's `burn_in_daylight` tag | Sunlight ignites the player when the head slot is empty. |
| Wall climbing | Spider, cave spider | Horizontal contact climbs a wall. Jump under a ceiling holds the player against it. |
| Bounce movement | Slime, magma cube, sulfur cube | Ground movement has no horizontal speed. Air movement works, so the player must jump to travel. |
| Lava-safe | Blaze, magma cube, strider, wither, wither skeleton | Fire is cleared. The form receives fire resistance. |
| Jump and movement | Every form | Killshift copies the source attribute values. |
| No fall damage | Cat, chicken, and all forced-flight forms | Fall damage multiplier is zero. Chicken descent is also limited to a slow speed. |
| Screen effect | Creeper, spider, cave spider, enderman, endermite, shulker | The server applies the creeper, spider, or invert post effect. It removes its effect when the form ends. |

## Active and reactive behavior

Use an empty hand to activate a right-click ability.

| Form | Behavior |
| --- | --- |
| Bee | Damage makes the bee angry for 10 seconds. Anger increases flight speed. A melee hit poisons the target. Right-click a flower with an empty hand to heal. |
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
| Evoker | Right-click raises a line of evoker fangs. |
| Ghast | Right-click shoots an explosive fireball. |
| Glow squid | Right-click releases squid ink particles. |
| Guardian | A melee hit gives mining fatigue to the target. |
| Llama | Right-click spits in the look direction. |
| Mooshroom | Right-click restores six hunger points and adds a bowl. |
| Parched | A bow receives a replacement arrow when no arrow remains. |
| Shulker | Right-click teleports to a random valid position. |
| Silverfish | Right-click an infestable block to remove it and summon an allied silverfish. |
| Skeleton | A bow receives a replacement arrow when no arrow remains. |
| Sniffer | Right-click on a grass block finds torchflower seeds or a pitcher pod. |
| Squid | Right-click releases squid ink particles. |
| Stray | A bow receives a replacement arrow when no arrow remains. |
| Trader llama | Right-click spits in the look direction. |
| Vex | The server gives the player no-physics movement and forced flight. The vanilla client can still block movement through walls. |
| Warden | Right-click fires a sonic boom at the entity in the look direction. |
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
| Chicken | Slow falling and no fall damage |
| Cod | Aquatic movement and water breathing |
| Copper golem | Common |
| Cow | Common |
| Creaking | Freeze while watched |
| Creeper | Explosion, skeleton peace, and creeper post effect |
| Dolphin | Aquatic movement |
| Donkey | Source jump strength |
| Drowned | Aquatic movement and sunlight rule |
| Elder guardian | Aquatic movement, water breathing, and mining-fatigue hit |
| Enderman | Pearl, wet damage, watched speed, and invert post effect |
| Endermite | Invert post effect |
| Ender dragon | Forced flight, dragon fireball, and no fall damage |
| Evoker | Fang attack |
| Fox | Common |
| Frog | Common |
| Ghast | Forced flight, explosive fireball, and no fall damage |
| Happy ghast | Forced flight and no fall damage |
| Glow squid | Aquatic movement, water breathing, and ink |
| Goat | Source jump strength |
| Guardian | Aquatic movement, water breathing, and mining-fatigue hit |
| Hoglin | Common |
| Horse | Source jump strength |
| Husk | Common |
| Illusioner | Common |
| Iron golem | Common |
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
| Sheep | Common |
| Shulker | Random teleport and invert post effect |
| Silverfish | Infest a block to summon an ally |
| Skeleton | Sunlight rule, arrow refill, and creeper peace |
| Skeleton horse | Source jump strength |
| Slime | Bounce movement |
| Sniffer | Ancient seed digging |
| Snow golem | Common |
| Spider | Climbing and spider post effect |
| Squid | Aquatic movement, water breathing, and ink |
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
| Player | Dead player's skin on a mannequin; normal player food rules |
