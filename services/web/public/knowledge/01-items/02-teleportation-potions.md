====
id: teleportation-potions
unlockOrder: 2
chatMessage: You've unlocked knowledge on the server's teleportation potions.
sidebarTitle: Teleportation Potions
====

There are 3 teleportation potions available on the server, all of which are **supremely useful** for getting around quickly.

:::tip
For all of these potions, if you have a tamed animal *following you* (not sitting), or an animal leashed, they will teleport with you.
:::

## Potion of Displacement

This potion has the same effect that some may know as the `/rtp` command (random teleport) from previous years server.

It teleports you somewhere random in a radius of 10000 blocks. This can be great for finding new biomes & resources.

It can be crafted in these two ways:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Painting", "asset": "minecraft:painting", "wikiUrl": "https://minecraft.wiki/w/Painting" },
    { "pos": "ML", "name": "Painting", "asset": "minecraft:painting", "wikiUrl": "https://minecraft.wiki/w/Painting" },
    { "pos": "M", "name": "Water Bottle", "asset": "https://minecraft.wiki/images/Invicon_Water_Bottle.png", "wikiUrl": "https://minecraft.wiki/w/Water_Bottle" },
    { "pos": "MR", "name": "Painting", "asset": "minecraft:painting", "wikiUrl": "https://minecraft.wiki/w/Painting" },
    { "pos": "BM", "name": "Ender Pearl", "asset": "minecraft:ender_pearl", "wikiUrl": "https://minecraft.wiki/w/Ender_Pearl" }
  ],
  "output": { "name": "Potion of Displacement", "asset": "mainmod:charm-potion-displacement", "count": 4 }
}
```

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Chorus Fruit", "asset": "minecraft:chorus_fruit", "wikiUrl": "https://minecraft.wiki/w/Chorus_Fruit" },
    { "pos": "ML", "name": "Chorus Fruit", "asset": "minecraft:chorus_fruit", "wikiUrl": "https://minecraft.wiki/w/Chorus_Fruit" },
    { "pos": "M", "name": "Water Bottle", "asset": "https://minecraft.wiki/images/Invicon_Water_Bottle.png", "wikiUrl": "https://minecraft.wiki/w/Water_Bottle" },
    { "pos": "MR", "name": "Chorus Fruit", "asset": "minecraft:chorus_fruit", "wikiUrl": "https://minecraft.wiki/w/Chorus_Fruit" },
    { "pos": "BM", "name": "Ender Pearl", "asset": "minecraft:ender_pearl", "wikiUrl": "https://minecraft.wiki/w/Ender_Pearl" }
  ],
  "output": { "name": "Potion of Displacement", "asset": "mainmod:charm-potion-displacement", "count": 4 }
}
```

But now you're somewhere off 10000 blocks away from your home. That'll be quite the walk! Or, alternatively, you could use the:

## Potion of Returning

This potion teleports you to the world spawn of the server, the place you started when joining the server for the first time.

:::context
It teleports you to the world spawn instead of to your own home or respawn point to avoid everybody building super far away from each other and never seeing each other like last year. Don't worry though, minecart speed has been boosted to 20 blocks/second, which is incredibly quick. (Also, [you can craft powered rails with copper now](/play/knowledge/crafting-changes).) A minecart track from world spawn to your house should do the trick. Plus it make it easier for others to visit you! 🙂
:::

Here's how to teleport to the world spawn from anywhere:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Compass", "asset": "minecraft:compass", "wikiUrl": "https://minecraft.wiki/w/Compass" },
    { "pos": "ML", "name": "Lapis Lazuli", "asset": "minecraft:lapis_lazuli", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli" },
    { "pos": "M", "name": "Water Bottle", "asset": "https://minecraft.wiki/images/Invicon_Water_Bottle.png", "wikiUrl": "https://minecraft.wiki/w/Water_Bottle" },
    { "pos": "MR", "name": "Lapis Lazuli", "asset": "minecraft:lapis_lazuli", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli" },
    { "pos": "BM", "name": "Ender Pearl", "asset": "minecraft:ender_pearl", "wikiUrl": "https://minecraft.wiki/w/Ender_Pearl" }
  ],
  "output": { "name": "Potion of Returning", "asset": "mainmod:charm-potion-returning", "count": 4 }
}
```

## Potion of Resonance

This potion allows you to teleport directly to another player currently playing on the server.

So the magic of the potion knows what player to hone in on and teleport you to, you both must indulge in a dance of synchronicity.

Hold an item, any item, in your offhand while you drink the potion with your main hand. You will then be teleported to any player that is also holding the same item in either of their hands at the same moment.

The person teleporting must drink the potion, the person being teleported to must only coordinate with the other player regarding what item to hold.

Here's how to teleport to a player that is currently holding the same item you are holding in your offhand:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Compass", "asset": "minecraft:compass", "wikiUrl": "https://minecraft.wiki/w/Compass" },
    { "pos": "ML", "name": "Amethyst Shard", "asset": "minecraft:amethyst_shard", "wikiUrl": "https://minecraft.wiki/w/Amethyst_Shard" },
    { "pos": "M", "name": "Water Bottle", "asset": "https://minecraft.wiki/images/Invicon_Water_Bottle.png", "wikiUrl": "https://minecraft.wiki/w/Water_Bottle" },
    { "pos": "MR", "name": "Amethyst Shard", "asset": "minecraft:amethyst_shard", "wikiUrl": "https://minecraft.wiki/w/Amethyst_Shard" },
    { "pos": "BM", "name": "Ender Pearl", "asset": "minecraft:ender_pearl", "wikiUrl": "https://minecraft.wiki/w/Ender_Pearl" }
  ],
  "output": { "name": "Potion of Resonance", "asset": "mainmod:charm-potion-resonance", "count": 4 }
}
```
