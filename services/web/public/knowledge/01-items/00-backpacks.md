====
id: backpacks
unlockOrder: 1
chatMessage: You've unlocked knowledge on backpacks, a powerful early game shulker alternative.
sidebarTitle: Backpacks
====

When you right click a backpack, they open up an additional inventory for you to drop off your stuff. You can think of it as a chest in item form. This is quite powerful because you can upgrade them to store as much as a double chest, and you can have multiple at once.

![Leather Backpack Usage Example](/assets/knowledge/items/backpacks/usage_example.png)

You cannot put backpacks, Shulkers, or bundles inside backpacks. You also cannot put backpacks inside bundles.

Backpacks are crafted and come in 6 different levels, each with one more row of inventory space.

The Leather Backpack has 1 row / 9 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "TM", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "TR", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "ML", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "M", "name": "Chest", "tooltip": "A normal wooden chest.", "asset": "https://static.wikia.nocookie.net/minecraft_gamepedia/images/3/3d/Chest_%28S%29_JE1.png/revision/latest?cb=20200128020353", "wikiUrl": "https://minecraft.wiki/w/Chest" },
    { "pos": "MR", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "BL", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "BM", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" },
    { "pos": "BR", "name": "Leather", "asset": "minecraft:leather", "wikiUrl": "https://minecraft.wiki/w/Leather" }
  ],
  "output": {
    "name": "Leather Backpack",
    "tooltip": "Holds 9 slots.",
    "asset": "mainmod:charm-leather-backpack"
  }
}
```

The Ingot Backpack has 2 rows / 18 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Iron Ingot", "asset": "minecraft:iron_ingot", "wikiUrl": "https://minecraft.wiki/w/Iron_Ingot" },
    { "pos": "TM", "name": "Block of Copper", "asset": "minecraft:copper_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Copper" },
    { "pos": "TR", "name": "Iron Ingot", "asset": "minecraft:iron_ingot", "wikiUrl": "https://minecraft.wiki/w/Iron_Ingot" },
    { "pos": "ML", "name": "Block of Copper", "asset": "minecraft:copper_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Copper" },
    { "pos": "M", "name": "Leather Backpack", "tooltip": "The previous backpack tier. Its contents are kept.", "asset": "mainmod:charm-leather-backpack" },
    { "pos": "MR", "name": "Block of Copper", "asset": "minecraft:copper_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Copper" },
    { "pos": "BL", "name": "Iron Ingot", "asset": "minecraft:iron_ingot", "wikiUrl": "https://minecraft.wiki/w/Iron_Ingot" },
    { "pos": "BM", "name": "Block of Copper", "asset": "minecraft:copper_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Copper" },
    { "pos": "BR", "name": "Iron Ingot", "asset": "minecraft:iron_ingot", "wikiUrl": "https://minecraft.wiki/w/Iron_Ingot" }
  ],
  "output": {
    "name": "Ingot Backpack",
    "tooltip": "Holds 18 slots.",
    "asset": "mainmod:charm-ingot-backpack"
  }
}
```

The Magic Backpack (*not actually magic in any way, sorry about that*) has 3 rows / 27 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Block of Raw Gold", "asset": "minecraft:raw_gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Raw_Gold" },
    { "pos": "TM", "name": "Deepslate Lapis Lazuli Ore", "asset": "minecraft:deepslate_lapis_ore", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli_Ore" },
    { "pos": "TR", "name": "Block of Raw Gold", "asset": "minecraft:raw_gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Raw_Gold" },
    { "pos": "ML", "name": "Deepslate Lapis Lazuli Ore", "asset": "minecraft:deepslate_lapis_ore", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli_Ore" },
    { "pos": "M", "name": "Ingot Backpack", "tooltip": "The previous backpack tier. Its contents are kept.", "asset": "mainmod:charm-ingot-backpack" },
    { "pos": "MR", "name": "Deepslate Lapis Lazuli Ore", "asset": "minecraft:deepslate_lapis_ore", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli_Ore" },
    { "pos": "BL", "name": "Block of Raw Gold", "asset": "minecraft:raw_gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Raw_Gold" },
    { "pos": "BM", "name": "Deepslate Lapis Lazuli Ore", "asset": "minecraft:deepslate_lapis_ore", "wikiUrl": "https://minecraft.wiki/w/Lapis_Lazuli_Ore" },
    { "pos": "BR", "name": "Block of Raw Gold", "asset": "minecraft:raw_gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Raw_Gold" }
  ],
  "output": {
    "name": "Magic Backpack",
    "tooltip": "Holds 27 slots.",
    "asset": "mainmod:charm-magic-backpack"
  }
}
```

The Bejeweled Backpack has 4 rows / 36 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "TM", "name": "Block of Emerald", "asset": "minecraft:emerald_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Emerald" },
    { "pos": "TR", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "ML", "name": "Block of Emerald", "asset": "minecraft:emerald_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Emerald" },
    { "pos": "M", "name": "Magic Backpack", "tooltip": "The previous backpack tier. Its contents are kept.", "asset": "mainmod:charm-magic-backpack" },
    { "pos": "MR", "name": "Block of Emerald", "asset": "minecraft:emerald_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Emerald" },
    { "pos": "BL", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "BM", "name": "Block of Emerald", "asset": "minecraft:emerald_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Emerald" },
    { "pos": "BR", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" }
  ],
  "output": {
    "name": "Bejeweled Backpack",
    "tooltip": "Holds 36 slots.",
    "asset": "mainmod:charm-bejeweled-backpack"
  }
}
```

The Withered Backpack has 5 rows / 45 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand" },
    { "pos": "TM", "name": "Nether Star", "asset": "minecraft:nether_star", "wikiUrl": "https://minecraft.wiki/w/Nether_Star" },
    { "pos": "TR", "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand" },
    { "pos": "ML", "name": "Netherite Scrap", "asset": "minecraft:netherite_scrap", "wikiUrl": "https://minecraft.wiki/w/Netherite_Scrap" },
    { "pos": "M", "name": "Bejeweled Backpack", "tooltip": "The previous backpack tier. Its contents are kept.", "asset": "mainmod:charm-bejeweled-backpack" },
    { "pos": "MR", "name": "Netherite Scrap", "asset": "minecraft:netherite_scrap", "wikiUrl": "https://minecraft.wiki/w/Netherite_Scrap" },
    { "pos": "BL", "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand" },
    { "pos": "BM", "name": "Netherite Scrap", "asset": "minecraft:netherite_scrap", "wikiUrl": "https://minecraft.wiki/w/Netherite_Scrap" },
    { "pos": "BR", "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand" }
  ],
  "output": {
    "name": "Withered Backpack",
    "tooltip": "Holds 45 slots.",
    "asset": "mainmod:charm-withered-backpack"
  }
}
```

The Endless Backpack (*not actually endless at all, sorry about that*) has 6 rows / 54 slots and is crafted like this:

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "End Stone", "asset": "minecraft:end_stone", "wikiUrl": "https://minecraft.wiki/w/End_Stone" },
    { "pos": "TM", "name": "Dragon Head", "asset": "https://minecraft.wiki/images/Invicon_Dragon_Head.png", "wikiUrl": "https://minecraft.wiki/w/Dragon_Head" },
    { "pos": "TR", "name": "End Stone", "asset": "minecraft:end_stone", "wikiUrl": "https://minecraft.wiki/w/End_Stone" },
    { "pos": "ML", "name": "Enderite Ingot", "asset": "mainmod:enderite-ingot", "knowledgeUrl": "/play/knowledge/enderite" },
    { "pos": "M", "name": "Withered Backpack", "tooltip": "The previous backpack tier. Its contents are kept.", "asset": "mainmod:charm-withered-backpack" },
    { "pos": "MR", "name": "Enderite Ingot", "asset": "mainmod:enderite-ingot", "knowledgeUrl": "/play/knowledge/enderite" },
    { "pos": "BL", "name": "End Stone", "asset": "minecraft:end_stone", "wikiUrl": "https://minecraft.wiki/w/End_Stone" },
    { "pos": "BM", "name": "Dragon Egg", "asset": "minecraft:dragon_egg", "wikiUrl": "https://minecraft.wiki/w/Dragon_Egg" },
    { "pos": "BR", "name": "End Stone", "asset": "minecraft:end_stone", "wikiUrl": "https://minecraft.wiki/w/End_Stone" }
  ],
  "output": {
    "name": "Endless Backpack",
    "tooltip": "Holds 54 slots.",
    "asset": "mainmod:charm-endless-backpack"
  }
}
```

(Don't worry - the ender dragon drops an egg every time she gets killed on this server.)
