====
id: cosmetics
unlockOrder: 3
chatMessage: You've unlocked knowledge on the basics of cosmetics
sidebarTitle: Cosmetics
====

Fashion Books unlock cosmetics in the shop. Some cosmetics are dyeable, and some also work as [decorative blocks](/play/knowledge/decorative-blocks).

## Dye a cosmetic

Combine one dyeable cosmetic with one or more dyes. Repeating a dye gives that colour more weight in the result.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "ML", "name": "Dyeable Cosmetic", "asset": "mainmod:cosmetic-beanie" },
    { "pos": "M", "name": "Red Dye", "asset": "minecraft:red_dye", "wikiUrl": "https://minecraft.wiki/w/Red_Dye" },
    { "pos": "MR", "name": "Red Dye", "tooltip": "This repeated dye has twice the weight.", "asset": "minecraft:red_dye", "wikiUrl": "https://minecraft.wiki/w/Red_Dye" },
    { "pos": "BM", "name": "Blue Dye", "asset": "minecraft:blue_dye", "wikiUrl": "https://minecraft.wiki/w/Blue_Dye" }
  ],
  "output": { "name": "Dyed Cosmetic", "tooltip": "The cosmetic keeps its model and receives the blended colour.", "asset": "mainmod:cosmetic-beanie" }
}
```

## Equip and remove a cosmetic

Combine one cosmetic with one compatible armour item. The armour takes the cosmetic's appearance and colour.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Helmet", "asset": "minecraft:diamond_helmet", "wikiUrl": "https://minecraft.wiki/w/Helmet" },
    { "pos": "MR", "name": "Cosmetic", "asset": "mainmod:cosmetic-beanie" }
  ],
  "output": { "name": "Helmet with Cosmetic", "tooltip": "The helmet keeps its properties and uses the cosmetic appearance.", "asset": "mainmod:cosmetic-beanie" }
}
```

Put the cosmetic helmet into a crafting grid by itself to separate it. The cosmetic is the recipe output, and the original helmet remains in the grid as the recipe remainder.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Helmet with Cosmetic", "asset": "mainmod:cosmetic-beanie" }
  ],
  "output": { "name": "Cosmetic", "asset": "mainmod:cosmetic-beanie" }
}
```

## Bow trails

Combine one bow with one or more dyes to create a coloured trail. Repeated dyes increase that colour's weight.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "ML", "name": "Bow", "asset": "minecraft:bow", "wikiUrl": "https://minecraft.wiki/w/Bow" },
    { "pos": "M", "name": "Red Dye", "asset": "minecraft:red_dye", "wikiUrl": "https://minecraft.wiki/w/Red_Dye" },
    { "pos": "MR", "name": "Red Dye", "tooltip": "This repeated dye has twice the weight.", "asset": "minecraft:red_dye", "wikiUrl": "https://minecraft.wiki/w/Red_Dye" },
    { "pos": "BM", "name": "Blue Dye", "asset": "minecraft:blue_dye", "wikiUrl": "https://minecraft.wiki/w/Blue_Dye" }
  ],
  "output": { "name": "Bow with Trail", "asset": "minecraft:bow" }
}
```

Put a bow into the grid by itself to remove its trail.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Bow with Trail", "asset": "minecraft:bow", "wikiUrl": "https://minecraft.wiki/w/Bow" }
  ],
  "output": { "name": "Bow", "asset": "minecraft:bow", "wikiUrl": "https://minecraft.wiki/w/Bow" }
}
```

Some cosmetics use animated textures. The animation continues when you equip the cosmetic.
