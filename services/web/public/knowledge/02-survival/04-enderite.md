====
id: enderite
unlockOrder: 5
chatMessage: You've unlocked knowledge on Enderite equipment.
sidebarTitle: Enderite
====

Enderite upgrades Netherite equipment and gives armour extra [charm slots](/play/knowledge/charms).

## Enderite Ingot

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Enderite Scrap", "asset": "mainmod:enderite-scrap" },
    { "pos": "TM", "name": "Enderite Scrap", "asset": "mainmod:enderite-scrap" },
    { "pos": "TR", "name": "Enderite Scrap", "asset": "mainmod:enderite-scrap" },
    { "pos": "ML", "name": "Enderite Scrap", "asset": "mainmod:enderite-scrap" },
    { "pos": "M", "name": "Block of Gold", "asset": "minecraft:gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Gold" },
    { "pos": "MR", "name": "Block of Gold", "asset": "minecraft:gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Gold" },
    { "pos": "BM", "name": "Block of Gold", "asset": "minecraft:gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Gold" },
    { "pos": "BR", "name": "Block of Gold", "asset": "minecraft:gold_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Gold" }
  ],
  "output": { "name": "Enderite Ingot", "asset": "mainmod:enderite-ingot" }
}
```

## Duplicate the smithing template

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "TM", "name": "Enderite Upgrade Smithing Template", "asset": "mainmod:enderite-upgrade-smithing-template" },
    { "pos": "TR", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "ML", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "M", "name": "End Stone", "asset": "minecraft:end_stone", "wikiUrl": "https://minecraft.wiki/w/End_Stone" },
    { "pos": "MR", "name": "Diamond", "asset": "minecraft:diamond", "wikiUrl": "https://minecraft.wiki/w/Diamond" },
    { "pos": "BL", "name": "Block of Diamond", "asset": "minecraft:diamond_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Diamond" },
    { "pos": "BM", "name": "Netherite Ingot", "asset": "minecraft:netherite_ingot", "wikiUrl": "https://minecraft.wiki/w/Netherite_Ingot" },
    { "pos": "BR", "name": "Block of Diamond", "asset": "minecraft:diamond_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Diamond" }
  ],
  "output": { "name": "Enderite Upgrade Smithing Template", "asset": "mainmod:enderite-upgrade-smithing-template", "count": 2 }
}
```

## Upgrade Netherite equipment

Use a smithing table with the template, one supported Netherite item, and one Enderite Ingot. The recipe supports Netherite armour, swords, spears, pickaxes, axes, shovels, and hoes.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "ML", "name": "Enderite Upgrade Smithing Template", "tooltip": "Put this in the template slot of a smithing table.", "asset": "mainmod:enderite-upgrade-smithing-template" },
    { "pos": "M", "name": "Supported Netherite Equipment", "tooltip": "Put one supported Netherite item in the base slot.", "asset": "minecraft:netherite_chestplate", "wikiUrl": "https://minecraft.wiki/w/Netherite" },
    { "pos": "MR", "name": "Enderite Ingot", "tooltip": "Put this in the addition slot.", "asset": "mainmod:enderite-ingot" }
  ],
  "output": { "name": "Enderite Equipment", "tooltip": "The output keeps the base item and marks it as Enderite equipment.", "asset": "minecraft:netherite_chestplate" }
}
```

The checked-in source does not yet define how Alien Debris generates or becomes Enderite Scrap. This page does not invent those missing steps.
