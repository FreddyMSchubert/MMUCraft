====
id: bow-trails
unlockOrder: 5
chatMessage: You unlocked Bow and Elytra Trails. Add particles to your arrows and flights.
sidebarTitle: Bow and Elytra Trails
tags:
- bowtrails
- bow trails
- elytra trails
- particles
- member particles
tips:
- Craft a bow or elytra with particle ingredients to add to its trail.
- Craft a bow or elytra alone to clear all of its trail particles.
- Members can use special particles. Other players can use coloured dust.
====

Add a particle trail to a bow or elytra. Arrows leave particles while they move. An elytra leaves particles while you glide.

## Add, mix, or clear particles

Put **one bow or one elytra** in a crafting grid. Add any ingredients from the tables below. The slot positions do not matter. Both the inventory grid and the crafting table work.

Each occupied ingredient slot adds **one weight** to that particle. Crafting consumes one item from each ingredient slot. A stack of 64 in one slot adds one weight, not 64. Use more slots or craft again to increase a weight.

New ingredients **add to the saved trail**. They do not replace it. Repeated ingredients increase the chance of that particle. To clear all particles, craft the bow or elytra **alone**. This does not return the ingredients.

Names, damage, enchantments, other custom data, and other lore stay on the item. Existing dyed bows retain their colours.

:::tip Read the tooltip
The tooltip lists all saved particles. The percentage in brackets is the chance for each particle when the full trail is active. For example, `Red dust (50%)` means that half of the selections use red dust on average. Percentages are rounded to two decimal places. Very small chances show as `<0.01%`.
:::

If the saved trail has two red weights and one blue weight, add one blue dye to make the chances **50% red and 50% blue**. If you then add one blaze powder, the full trail becomes **40% red, 40% blue, and 20% flame**.

## Membership

:::perk Special particles
All particles except ordinary coloured dust require membership. This applies to both bows and elytra. The server checks the player before it selects particles. The person who crafted the item does not control access.
:::

Anyone can craft and keep the ingredients on an item. For a non-member, the server selects only dust and recalculates the chances across those dust weights. In the example above, a non-member gets **50% red and 50% blue**. If an item has no dust, a non-member gets no custom trail.

Special particles stay saved on the item. They become active when a member uses it. The tooltip marks them with `[Member]`. Redstone produces ordinary red dust, so it is available to everyone. Sculk colour fade is a different particle type and requires membership.

## Flight and particle behaviour

Elytra particles start at least two blocks behind the direction of travel. The server sends them to nearby players and excludes the wearer. **You do not see your own elytra trail**, including in third-person view. This keeps particles out of your view during tight turns and dives. Other players can see the trail.

Particles stop when you stop gliding. Arrow particles stop when the arrow stops in a block, is removed, or reaches 30 seconds of age. Trails do not change critical-hit damage.

Small trails select two particles per game tick. A member trail with an explosion, sonic boom, gust, shriek, campfire smoke, sweeping arc, or noxious gas selects two particles every ten ticks. The entire mix uses this slower rate, so the listed chances stay correct. A non-member's dust uses the normal rate.

All effects are visual. Flames do not burn blocks. Explosions and sonic booms do not cause damage. Omen, infested, and soul effects do not apply status effects. Your particle settings and resource pack can change what you see.

## Dust ingredients: available to everyone

Each dye uses its own colour. The shared wiki preview shows the dust shape in red. **The dye name determines your trail colour.**

| Ingredient | Particle | Example |
| --- | --- | --- |
| White Dye | White dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="White dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Orange Dye | Orange dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Orange dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Magenta Dye | Magenta dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Magenta dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Light Blue Dye | Light blue dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Light blue dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Yellow Dye | Yellow dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Yellow dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Lime Dye | Lime dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Lime dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Pink Dye | Pink dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Pink dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Gray Dye | Gray dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Gray dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Light Gray Dye | Light gray dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Light gray dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Cyan Dye | Cyan dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Cyan dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Purple Dye | Purple dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Purple dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Blue Dye | Blue dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Blue dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Brown Dye | Brown dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Brown dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Green Dye | Green dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Green dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Red Dye | Red dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Red dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Black Dye | Black dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Black dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Redstone Dust | Redstone dust<br><code>dust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_particle_example_JE.png/150px-Dust_particle_example_JE.png?64944" alt="Redstone dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |

## Special ingredients: member effects

Each row is one ingredient and one saved particle choice. The ingredients do not require a specific arrangement. A preview can show several particles or the vanilla event that produces them. The trail uses the particle described in the row.

| Ingredient | Particle | Example |
| --- | --- | --- |
| Enchanting Table | Enchanting letters<br><code>enchant</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Enchant_particle_example_JE.png/150px-Enchant_particle_example_JE.png?6347a" alt="Enchanting letters particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Lapis Lazuli | Enchanted sparks<br><code>enchanted_hit</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Enchanted_hit_particle_example_JE.png/150px-Enchanted_hit_particle_example_JE.png?2a1f7" alt="Enchanted sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| End Rod | End rod sparks<br><code>end_rod</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/End_rod_particle_example_JE.png/150px-End_rod_particle_example_JE.png?a0238" alt="End rod sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Obsidian | Portal<br><code>portal</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Basic_portal_particle_example.png/150px-Basic_portal_particle_example.png?51e0c" alt="Portal particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Ender Pearl | Reverse portal<br><code>reverse_portal</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Reverse_portal_particle_example_JE.png/150px-Reverse_portal_particle_example_JE.png?4e128" alt="Reverse portal particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Fermented Spider Eye | Witch magic<br><code>witch</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Witch_particle_example_JE.png/150px-Witch_particle_example_JE.png?52482" alt="Witch magic particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Firework Rocket | Firework sparks<br><code>firework</code><br>Uses white sparks. Rocket star colours do not change the trail. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Firework_particle_example_JE.png/150px-Firework_particle_example_JE.png?4d9d7" alt="Firework sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Totem of Undying | Totem sparks<br><code>totem_of_undying</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Totem_of_undying_particle_example_JE.png/150px-Totem_of_undying_particle_example_JE.png?eaa7b" alt="Totem sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Cherry Leaves | Cherry leaves<br><code>cherry_leaves</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Cherry_leaves_particle_example_JE.png/150px-Cherry_leaves_particle_example_JE.png?608f2" alt="Cherry leaves particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Pale Oak Leaves | Pale oak leaves<br><code>pale_oak_leaves</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Pale_Oak_Leaves_particle_example.png/150px-Pale_Oak_Leaves_particle_example.png?66ee2" alt="Pale oak leaves particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Blaze Powder | Flame<br><code>flame</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Flame_particle_example_JE.png/150px-Flame_particle_example_JE.png?6c263" alt="Flame particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Soul Torch | Soul flame<br><code>soul_fire_flame</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Soul_fire_flame_particle_example_JE.png/150px-Soul_fire_flame_particle_example_JE.png?08127" alt="Soul flame particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Copper Torch | Copper flame<br><code>copper_fire_flame</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Copper_fire_flame_particle_example_JE.png/150px-Copper_fire_flame_particle_example_JE.png?c98f5" alt="Copper flame particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Magma Cream | Lava sparks<br><code>lava</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Lava_particle_example_JE.png/150px-Lava_particle_example_JE.png?00e14" alt="Lava sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Poppy | Hearts<br><code>heart</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Heart_particle_example_JE.png/150px-Heart_particle_example_JE.png?5b75a" alt="Hearts particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Soul Sand | Souls<br><code>soul</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Soul_particle_example_JE.png/150px-Soul_particle_example_JE.png?4666e" alt="Souls particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sculk | Sculk sparks<br><code>sculk_charge_pop</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sculk_charge_pop_particle_example_JE.png/150px-Sculk_charge_pop_particle_example_JE.png?04dda" alt="Sculk sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| TNT | Explosion<br><code>explosion</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Explosion_particle_example_JE.png/150px-Explosion_particle_example_JE.png?394ab" alt="Explosion particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Echo Shard | Sonic boom<br><code>sonic_boom</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sonic_boom_particle_example_JE.png/150px-Sonic_boom_particle_example_JE.png?af773" alt="Sonic boom particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Emerald | Happy villager<br><code>happy_villager</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Single_crop_growth_particle_example.png/150px-Single_crop_growth_particle_example.png?6a2db" alt="Happy villager particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Rotten Flesh | Angry villager<br><code>angry_villager</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Angry_villager_particle_example_JE.png/150px-Angry_villager_particle_example_JE.png?321d2" alt="Angry villager particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Turtle Egg | Egg sparks<br><code>egg_crack</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Egg_crack_particle_example_JE.png/150px-Egg_crack_particle_example_JE.png?14ad9" alt="Egg sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Note Block | Music notes<br><code>note</code><br>Selects a random note colour for each particle. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Note_particle_example_JE.png/150px-Note_particle_example_JE.png?2f4c4" alt="Music notes particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Firefly Bush | Fireflies<br><code>firefly</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Firefly_Bushes_in_Swamp.gif/150px-Firefly_Bushes_in_Swamp.gif?fc322" alt="Fireflies particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Nautilus Shell | Nautilus<br><code>nautilus</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Nautilus_particle_example_JE.png/150px-Nautilus_particle_example_JE.png?edb3b" alt="Nautilus particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Spider Eye | Infested<br><code>infested</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Infested_particle_example_JE1.png/150px-Infested_particle_example_JE1.png?26e3b" alt="Infested particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Breeze Rod | Small gust<br><code>small_gust</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Small_gust_particle_example_JE1.png/150px-Small_gust_particle_example_JE1.png?49de6" alt="Small gust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Wind Charge | Gust<br><code>gust</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Gust_particle_example_JE.png/150px-Gust_particle_example_JE.png?09eab" alt="Gust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Campfire | Campfire smoke<br><code>campfire_cosy_smoke</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Campfire_cosy_smoke_particle_example_JE.png/150px-Campfire_cosy_smoke_particle_example_JE.png?4f845" alt="Campfire smoke particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Feather | Cloud<br><code>cloud</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Cloud_particle_example_JE.png/150px-Cloud_particle_example_JE.png?89ae4" alt="Cloud particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Charcoal | Smoke<br><code>smoke</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Smoke_particle_example_JE.png/150px-Smoke_particle_example_JE.png?96c97" alt="Smoke particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Bone | Poof<br><code>poof</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Poof_particle_example_JE.png/150px-Poof_particle_example_JE.png?740eb" alt="Poof particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Bamboo | Sneeze<br><code>sneeze</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sneeze_particle_example_JE.png/150px-Sneeze_particle_example_JE.png?a7812" alt="Sneeze particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Hay Bale | Llama spit<br><code>spit</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Spit_particle_example_JE.png/150px-Spit_particle_example_JE.png?c2add" alt="Llama spit particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Ink Sac | Squid ink<br><code>squid_ink</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Squid_ink_particle_example_JE.png/150px-Squid_ink_particle_example_JE.png?00a9c" alt="Squid ink particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Glow Ink Sac | Glow squid ink<br><code>glow_squid_ink</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Glow_ink_particle_example_JE.png/150px-Glow_ink_particle_example_JE.png?53866" alt="Glow squid ink particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Glow Berries | Glow<br><code>glow</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Glow_particle_example_JE.png/150px-Glow_particle_example_JE.png?9bbed" alt="Glow particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Lightning Rod | Electric sparks<br><code>electric_spark</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Electric_spark_particle_example_JE.png/150px-Electric_spark_particle_example_JE.png?b4ea1" alt="Electric sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Honeycomb | Wax sparks<br><code>wax_on</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Wax_on_particle_example_JE.png/150px-Wax_on_particle_example_JE.png?3628b" alt="Wax sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Oxidized Copper | Copper scrape<br><code>scrape</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Scrape_particle_example_JE.png/150px-Scrape_particle_example_JE.png?dbd18" alt="Copper scrape particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Flint | Critical sparks<br><code>crit</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Crit_particle_example_JE.png/150px-Crit_particle_example_JE.png?8527c" alt="Critical sparks particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sweet Berries | Damage hearts<br><code>damage_indicator</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Damage_indicator_particle_example_JE.png/150px-Damage_indicator_particle_example_JE.png?4f76d" alt="Damage hearts particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Iron Sword | Sweeping arc<br><code>sweep_attack</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sweep_attack_particle_example_JE.png/150px-Sweep_attack_particle_example_JE.png?df4e4" alt="Sweeping arc particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Slimeball | Slime<br><code>item_slime</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Item_slime_particle_example_JE.png/150px-Item_slime_particle_example_JE.png?bc5ee" alt="Slime particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Cobweb | Cobweb<br><code>item_cobweb</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Item_cobweb_particle_example_JE1.png/150px-Item_cobweb_particle_example_JE1.png?1066a" alt="Cobweb particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Snowball | Snowball<br><code>item_snowball</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Item_snowball_particle_example_JE.png/150px-Item_snowball_particle_example_JE.png?8f997" alt="Snowball particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Snow Block | Snowflakes<br><code>snowflake</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Snowflake_particle_example_JE.png/150px-Snowflake_particle_example_JE.png?e9ae2" alt="Snowflakes particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Prismarine Shard | Water splash<br><code>splash</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Splash_particle_example_JE.png/150px-Splash_particle_example_JE.png?50a3d" alt="Water splash particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Prismarine Crystals | Bubble pop<br><code>bubble_pop</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Particle_bubble_pop.png/150px-Particle_bubble_pop.png?472a9" alt="Bubble pop particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Wet Sponge | Water drops<br><code>falling_water</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_water_particle_example_JE.png/150px-Falling_water_particle_example_JE.png?a9c34" alt="Water drops particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Lava Bucket | Lava drops<br><code>falling_lava</code><br>Returns an empty bucket. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_lava_particle_example_JE.png/150px-Falling_lava_particle_example_JE.png?5a5ee" alt="Lava drops particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Honey Bottle | Honey drops<br><code>falling_honey</code><br>Returns a glass bottle. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_honey_particle_example_JE.png/150px-Falling_honey_particle_example_JE.png?e5341" alt="Honey drops particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sunflower | Nectar<br><code>falling_nectar</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_nectar_particle_example_JE.png/150px-Falling_nectar_particle_example_JE.png?010af" alt="Nectar particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Crying Obsidian | Obsidian tears<br><code>falling_obsidian_tear</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_obsidian_tear_particle_example_JE.png/150px-Falling_obsidian_tear_particle_example_JE.png?8324c" alt="Obsidian tears particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Spore Blossom | Spore blossom<br><code>falling_spore_blossom</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_spore_blossom_particle_example_JE.png/150px-Falling_spore_blossom_particle_example_JE.png?7cca7" alt="Spore blossom particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Mycelium | Mycelium spores<br><code>mycelium</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Mycelium_particle_example_JE.png/150px-Mycelium_particle_example_JE.png?b49f7" alt="Mycelium spores particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Crimson Fungus | Crimson spores<br><code>crimson_spore</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Crimson_spore_particle_example_JE.png/150px-Crimson_spore_particle_example_JE.png?373d3" alt="Crimson spores particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Warped Fungus | Warped spores<br><code>warped_spore</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Warped_spore_particle_example_JE.png/150px-Warped_spore_particle_example_JE.png?f6c8b" alt="Warped spores particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Soul Soil | Ash<br><code>ash</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Ash_particle_example_JE.png/150px-Ash_particle_example_JE.png?752d3" alt="Ash particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Basalt | White ash<br><code>white_ash</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/White_ash_particle_example_JE.png/150px-White_ash_particle_example_JE.png?f75f7" alt="White ash particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Brush | Dust plume<br><code>dust_plume</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_plume_particle_example_JE.png/150px-Dust_plume_particle_example_JE.png?69c69" alt="Dust plume particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Trial Key | Trial detection<br><code>trial_spawner_detection</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Trial_spawner_particles.png/150px-Trial_spawner_particles.png?c2df3" alt="Trial detection particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Ominous Trial Key | Ominous trial detection<br><code>trial_spawner_detection_ominous</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Ominous_Trial_Spawner_Particles.png/150px-Ominous_Trial_Spawner_Particles.png?b6e93" alt="Ominous trial detection particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Diamond | Vault connection<br><code>vault_connection</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Vault_connection_example_JE.png/150px-Vault_connection_example_JE.png?4b498" alt="Vault connection particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Heavy Core | Ominous spawning<br><code>ominous_spawning</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Ominous_Item_Spawner.png/150px-Ominous_Item_Spawner.png?653a4" alt="Ominous spawning particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Ominous Bottle | Raid omen<br><code>raid_omen</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Raid_omen_particle_example_JE1.png/150px-Raid_omen_particle_example_JE1.png?df788" alt="Raid omen particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Chiseled Tuff Bricks | Trial omen<br><code>trial_omen</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Trial_omen_particle_example.png/150px-Trial_omen_particle_example.png?ea410" alt="Trial omen particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Potent Sulfur | Noxious gas<br><code>noxious_gas</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Noxious_gas_particle_example.png/150px-Noxious_gas_particle_example.png?94bb5" alt="Noxious gas particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Bucket of Sulfur Cube | Sulfur goo<br><code>sulfur_cube_goo</code><br>Consumes the cube. Returns an empty bucket. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sulfur_cube_goo_particle_example.png/150px-Sulfur_cube_goo_particle_example.png?94bb5" alt="Sulfur goo particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Oak Leaves | Green leaves<br><code>tinted_leaves</code><br>Uses a fixed green colour. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_leaves.jpeg/150px-Falling_leaves.jpeg?5eb24" alt="Green leaves particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Dragon's Breath | Dragon breath<br><code>dragon_breath</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dragon_breath_particle_example_JE.png/150px-Dragon_breath_particle_example_JE.png?af651" alt="Dragon breath particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Glowstone Dust | Potion swirls<br><code>effect</code><br>Uses purple swirls. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Effect_particle_example_JE.png/150px-Effect_particle_example_JE.png?d2a61" alt="Potion swirls particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Glistering Melon Slice | Instant effect<br><code>instant_effect</code><br>Uses red particles. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Instant_effect_particle_example_JE.png/150px-Instant_effect_particle_example_JE.png?928d4" alt="Instant effect particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sculk Sensor | Sculk colour fade<br><code>dust_color_transition</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Dust_color_transition_particle_example_JE.png/150px-Dust_color_transition_particle_example_JE.png?5d1ef" alt="Sculk colour fade particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sculk Vein | Sculk charge<br><code>sculk_charge</code> | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Sculk_charge_particle_example_JE.png/150px-Sculk_charge_particle_example_JE.png?f917c" alt="Sculk charge particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sculk Shrieker | Shriek<br><code>shriek</code><br>Uses the slower trail rate. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Shriek_particle_example_JE.png/150px-Shriek_particle_example_JE.png?3eee9" alt="Shriek particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Block of Amethyst | Amethyst fragments<br><code>block</code><br>Uses amethyst fragments. The wiki image shows a generic block example. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Block_particle_example_JE.png/150px-Block_particle_example_JE.png?0ed3d" alt="Amethyst fragments particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Sand | Sand dust<br><code>falling_dust</code><br>Uses sand. The wiki image shows the particle family. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Falling_dust_particle_example_JE.png/150px-Falling_dust_particle_example_JE.png?b8a59" alt="Sand dust particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Creaking Heart | Creaking fragments<br><code>block_crumble</code><br>Uses the default creaking-heart block state. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Block_crumble_particle_JE.png/150px-Block_crumble_particle_JE.png?4ef55" alt="Creaking fragments particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |
| Cookie | Cookie crumbs<br><code>item</code><br>Uses cookie crumbs. The wiki image shows a generic item example. | <a href="https://minecraft.wiki/w/Particles"><img src="https://minecraft.wiki/images/thumb/Item_particle_example_JE.png/150px-Item_particle_example_JE.png?960d0" alt="Cookie crumbs particle example" width="150" loading="lazy" referrerpolicy="no-referrer"></a> |

## Image source

The previews above link directly to images from the [Minecraft Wiki particle catalogue](https://minecraft.wiki/w/Particles). Images remain hosted by the wiki. Select a preview to open the source catalogue. Minecraft game textures and screenshots belong to their respective rights holders. The wiki provides [licensing information](https://meta.weirdgloop.org/w/Licensing).

The previews show the particle family. Colour, motion, density, background, and resource packs can differ from the trail. Fragment trails use the exact block or item stated in the table, even when the wiki image uses a different example.

## Catalogue scope and excluded particles

This list covers all **125 registered particle types in Java Edition 26.2**. The tables include **81 particle types** and **97 ingredient choices**. The extra choices are the dye colours that share the dust type. The remaining **44 types** are listed below. Each included type adds a distinct appearance or a requested effect. Similar variants use one representative where practical.

| Excluded particle | Reason |
| --- | --- |
| `block_marker` | Shows a technical block marker. It is not a trail effect. |
| `bubble` | Requires water. It disappears in air. |
| `bubble_column_up` | Requires water. It disappears in air. |
| `campfire_signal_smoke` | Larger, longer campfire smoke. The cosy smoke effect covers this appearance. |
| `composter` | A small green sparkle. Happy villager particles cover it. |
| `current_down` | Requires water. It is a downward bubble-column effect. |
| `dolphin` | Designed for a water trail. It has little use during flight. |
| `dripping_dripstone_lava` | A dripstone variant of lava drops. Use falling lava directly. |
| `dripping_dripstone_water` | A dripstone variant of water drops. Use falling water directly. |
| `dripping_honey` | A hanging stage before falling honey. Use falling honey directly. |
| `dripping_lava` | A hanging stage before falling lava. Use falling lava directly. |
| `dripping_obsidian_tear` | A hanging stage before falling obsidian tears. Use falling tears directly. |
| `dripping_water` | A hanging stage before falling water. Use falling water directly. |
| `dust_pillar` | Uses block fragments for a mace impact. The selected block effect covers fragments. |
| `elder_guardian` | Appears in the viewer's face regardless of its world position. It cannot form a rear trail. |
| `entity_effect` | Another coloured potion effect. Potion swirls and instant effects cover it. |
| `explosion_emitter` | Spawns many secondary explosion particles. Use a single explosion particle instead. |
| `falling_dripstone_lava` | A dripstone variant of falling lava. The selected lava drops cover it. |
| `falling_dripstone_water` | A dripstone variant of falling water. The selected water drops cover it. |
| `fishing` | A flat water-surface wake. It does not suit flight. |
| `flash` | A large, bright flash. Repeated flashes would obscure nearby players. |
| `geyser` | A terrain emitter with geyser parameters. It is not a compact moving trail. |
| `geyser_base` | A large geyser-base effect. Cloud and poof provide compact choices. |
| `geyser_plume` | A large vertical plume with geyser parameters. It does not suit a rear trail. |
| `geyser_poof` | Another geyser effect. The selected poof covers the small cloud appearance. |
| `gust_emitter_large` | Spawns many secondary gust particles. Use the gust effect instead. |
| `gust_emitter_small` | Spawns secondary gust particles. Use the small gust effect instead. |
| `landing_honey` | A stationary landing stage. Falling honey can produce it when it lands. |
| `landing_lava` | A stationary landing stage. Falling lava can produce it when it lands. |
| `landing_obsidian_tear` | A stationary landing stage. Falling tears can produce it when they land. |
| `large_smoke` | A larger form of smoke. Standard smoke and campfire smoke cover it. |
| `noxious_gas_cloud` | Another gas-cloud form. The selected noxious gas covers it. |
| `pause_mob_growth` | Another green sparkle. Happy villager particles cover it. |
| `rain` | Similar to the selected splash effect. Rainfall itself is a separate weather effect. |
| `reset_mob_growth` | Another green sparkle. Happy villager particles cover it. |
| `sculk_soul` | Similar to the selected soul effect. Sculk charge and sparks provide other sculk choices. |
| `small_flame` | A smaller flame. The standard flame covers this appearance. |
| `spore_blossom_air` | Similar to falling spore blossom. One ingredient provides the falling effect. |
| `sulfur_bubbles` | Requires a water context. Use sulfur goo or gas for an air trail. |
| `trail` | Needs a destination, colour, and duration. The selected sparks provide a simple position-based trail. |
| `underwater` | Small underwater specks. The visible spore effects cover this appearance. |
| `vibration` | Needs a destination and travel time. A moving destination could send it towards a player. |
| `wax_off` | A white spark variant. End rod and electric sparks cover it. |
| `white_smoke` | Similar to the selected cloud and poof effects. |

The wiki also lists Bedrock-only particles, Education effects, removed textures, weather effects, and future versions. Those entries are not additional Java 26.2 particle types. They cannot be selected by this server. This includes the future `yellow_poplar_leaves`, `orange_poplar_leaves`, and `red_poplar_leaves` types. Rainfall and snowfall screens, item-pickup animations, and unused texture files are not registered trail particles.

Some names on the wiki are names from another edition. For example, the Java registry uses `sulfur_bubbles`, `noxious_gas_cloud`, `gust_emitter_large`, and `gust_emitter_small`. The scope above uses the names from the server's Java registry.
