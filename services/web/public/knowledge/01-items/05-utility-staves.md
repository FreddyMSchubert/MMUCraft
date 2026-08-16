====
id: utility-staves
unlockOrder: 3
chatMessage: You've unlocked knowledge on the server's utility staves.
sidebarTitle: Utility Staves
====

Here's a few other fun things you may consider crafting.

## Staff of Soulbound Storage

Easily access your ender chest without ever placing down or bringing an actual ender chest, just wave the staff!

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TR", "name": "Ender Chest", "asset": "https://minecraft.wiki/images/thumb/Ender_Chest_%28S%29_JE2.png/150px-Ender_Chest_%28S%29_JE2.png", "wikiUrl": "https://minecraft.wiki/w/Ender_Chest" },
    { "pos": "M", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" },
    { "pos": "BL", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" }
  ],
  "output": { "name": "Staff of Soulbound Storage", "asset": "mainmod:charm-ender-chest-staff" }
}
```

## Staff of Brolly

The Staff of Brolly grants the holder a slow descent when holding it while falling.

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Red Wool", "asset": "minecraft:red_wool", "wikiUrl": "https://minecraft.wiki/w/Wool" },
    { "pos": "TR", "name": "White Wool", "asset": "minecraft:white_wool", "wikiUrl": "https://minecraft.wiki/w/Wool" },
    { "pos": "M", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" },
    { "pos": "MR", "name": "Red Wool", "asset": "minecraft:red_wool", "wikiUrl": "https://minecraft.wiki/w/Wool" },
    { "pos": "BL", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" }
  ],
  "output": { "name": "Staff of Brolly", "asset": "mainmod:charm-umbrella-staff" }
}
```

## Staff of Crafting

The Staff of Crafting brings up a full 3 by 3 crafting table UI, just by waving the staff once - no block placing necessary.

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TR", "name": "Crafting Table", "asset": "minecraft:crafting_table", "wikiUrl": "https://minecraft.wiki/w/Crafting_Table" },
    { "pos": "M", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" },
    { "pos": "BL", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" }
  ],
  "output": { "name": "Staff of Crafting", "asset": "mainmod:charm-crafting-staff" }
}
```

You may be wondering "*What's that blue stick thing down there?*". It's a **trident shaft**. You can find it in buried treasure chests:

![A buried treasure chest with some trident shafts spawned](/assets/knowledge/items/util_staves/buried_treasure.png)

### Trident

Of course, seeing as it's called a *Trident* Staff, you can also use it to make a Trident. This is a great alternative to getting a Trident without having to farm hundreds of Drowneds:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TM", "name": "Trident Prong", "asset": "mainmod:trident-prong" },
    { "pos": "TR", "name": "Trident Prong", "asset": "mainmod:trident-prong" },
    { "pos": "M", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" },
    { "pos": "MR", "name": "Trident Prong", "asset": "mainmod:trident-prong" },
    { "pos": "BL", "name": "Trident Shaft", "asset": "mainmod:trident-shaft" }
  ],
  "output": { "name": "Trident", "asset": "minecraft:trident", "wikiUrl": "https://minecraft.wiki/w/Trident" }
}
```

Those white thingies are *trident prongs*. Every elder guardian drops 1 trident prong when they are defeated.
