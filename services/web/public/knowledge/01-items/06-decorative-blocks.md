====
id: decorative-blocks
unlockOrder: 4
chatMessage: You've unlocked knowledge on custom decorative blocks.
sidebarTitle: Decorative Blocks
====

The server has custom decorations that you can place on suitable surfaces.

## Crafted decorations

### Cookie Jar

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Cookie", "asset": "minecraft:cookie", "wikiUrl": "https://minecraft.wiki/w/Cookie" },
    { "pos": "MR", "name": "Glass Bottle", "asset": "minecraft:glass_bottle", "wikiUrl": "https://minecraft.wiki/w/Glass_Bottle" }
  ],
  "output": { "name": "Cookie Jar", "asset": "mainmod:cookie-jar" }
}
```

### Firefly Jar

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Firefly Bush", "asset": "minecraft:firefly_bush", "wikiUrl": "https://minecraft.wiki/w/Firefly_Bush" },
    { "pos": "MR", "name": "Glass Bottle", "asset": "minecraft:glass_bottle", "wikiUrl": "https://minecraft.wiki/w/Glass_Bottle" }
  ],
  "output": { "name": "Firefly Jar", "asset": "mainmod:firefly-jar" }
}
```

### Fruit Bowl

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Bowl", "asset": "minecraft:bowl", "wikiUrl": "https://minecraft.wiki/w/Bowl" },
    { "pos": "TM", "name": "Apple", "asset": "minecraft:apple", "wikiUrl": "https://minecraft.wiki/w/Apple" },
    { "pos": "TR", "name": "Melon Slice", "asset": "minecraft:melon_slice", "wikiUrl": "https://minecraft.wiki/w/Melon_Slice" },
    { "pos": "ML", "name": "Pumpkin", "asset": "minecraft:pumpkin", "wikiUrl": "https://minecraft.wiki/w/Pumpkin" },
    { "pos": "M", "name": "Glistering Melon Slice", "asset": "minecraft:glistering_melon_slice", "wikiUrl": "https://minecraft.wiki/w/Glistering_Melon_Slice" },
    { "pos": "MR", "name": "Glow Berries", "asset": "minecraft:glow_berries", "wikiUrl": "https://minecraft.wiki/w/Glow_Berries" },
    { "pos": "BM", "name": "Sweet Berries", "asset": "minecraft:sweet_berries", "wikiUrl": "https://minecraft.wiki/w/Sweet_Berries" }
  ],
  "output": { "name": "Fruit Bowl", "asset": "mainmod:fruit-bowl" }
}
```

### Kettle

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "TM", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "TR", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "ML", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "MR", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "BL", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "BM", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" },
    { "pos": "BR", "name": "Copper Nugget", "asset": "minecraft:copper_nugget" }
  ],
  "output": { "name": "Kettle", "asset": "mainmod:kettle" }
}
```

### Vinyl Player

Use any stripped log and one of the server's five music discs. The disc images and names cycle in the center slot.

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Glass", "asset": "minecraft:glass", "wikiUrl": "https://minecraft.wiki/w/Glass" },
    { "pos": "TM", "name": "Glass", "asset": "minecraft:glass", "wikiUrl": "https://minecraft.wiki/w/Glass" },
    { "pos": "TR", "name": "Glass", "asset": "minecraft:glass", "wikiUrl": "https://minecraft.wiki/w/Glass" },
    { "pos": "ML", "name": "Glass", "asset": "minecraft:glass", "wikiUrl": "https://minecraft.wiki/w/Glass" },
    { "pos": "M", "name": "Music Disc", "asset": [
      { "src": "mainmod:disc-9am", "title": "9AM Music Disc" },
      { "src": "mainmod:disc-death", "title": "Death Music Disc" },
      { "src": "mainmod:disc-dog", "title": "Dog Music Disc" },
      { "src": "mainmod:disc-droopy-likes-your-face", "title": "Droopy Likes Your Face Music Disc" },
      { "src": "mainmod:disc-droopy-likes-ricochet", "title": "Droopy Likes Ricochet Music Disc" }
    ] },
    { "pos": "MR", "name": "Glass", "asset": "minecraft:glass", "wikiUrl": "https://minecraft.wiki/w/Glass" },
    { "pos": "BL", "name": "Any Stripped Log", "tooltip": "Any item in the server's stripped-logs tag works.", "asset": "minecraft:stripped_oak_log" },
    { "pos": "BM", "name": "Any Stripped Log", "tooltip": "Any item in the server's stripped-logs tag works.", "asset": "minecraft:stripped_oak_log" },
    { "pos": "BR", "name": "Any Stripped Log", "tooltip": "Any item in the server's stripped-logs tag works.", "asset": "minecraft:stripped_oak_log" }
  ],
  "output": { "name": "Vinyl Player", "asset": "mainmod:vinyl-player" }
}
```

### Spoons Carpets

Beer is required for every Spoons Carpet.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Mundane Potion", "asset": "https://minecraft.wiki/images/Invicon_Mundane_Potion.png", "wikiUrl": "https://minecraft.wiki/w/Mundane_Potion" },
    { "pos": "MR", "name": "Wheat", "asset": "minecraft:wheat", "wikiUrl": "https://minecraft.wiki/w/Wheat" }
  ],
  "output": { "name": "Beer", "asset": "mainmod:beer" }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Beer", "asset": "mainmod:beer" },
    { "pos": "MR", "name": "Any Carpet", "tooltip": "Any item in the server's carpets tag works.", "asset": "minecraft:white_carpet", "wikiUrl": "https://minecraft.wiki/w/Carpet" }
  ],
  "output": { "name": "Junoesque Spoons Carpet", "asset": "mainmod:spoons-carpet-junoesque" }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Beer", "asset": "mainmod:beer" },
    { "pos": "MR", "name": "Any Carpet", "tooltip": "Any item in the server's carpets tag works.", "asset": "minecraft:white_carpet", "wikiUrl": "https://minecraft.wiki/w/Carpet" },
    { "pos": "BM", "name": "Any Carpet", "tooltip": "Any item in the server's carpets tag works.", "asset": "minecraft:white_carpet", "wikiUrl": "https://minecraft.wiki/w/Carpet" }
  ],
  "output": { "name": "Grandiloquent Spoons Carpet", "asset": "mainmod:spoons-carpet-grandiloquent" }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "ML", "name": "Beer", "asset": "mainmod:beer" },
    { "pos": "M", "name": "Beer", "asset": "mainmod:beer" },
    { "pos": "MR", "name": "Any Carpet", "tooltip": "Any item in the server's carpets tag works.", "asset": "minecraft:white_carpet", "wikiUrl": "https://minecraft.wiki/w/Carpet" }
  ],
  "output": { "name": "Meretricious Spoons Carpet", "asset": "mainmod:spoons-carpet-meretricious" }
}
```

## Invisible item displays

Use an Invisi-carrot on an item frame to make the frame invisible.

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Potion of Invisibility", "asset": "https://minecraft.wiki/images/Invicon_Potion_of_Invisibility.png", "wikiUrl": "https://minecraft.wiki/w/Potion_of_Invisibility" },
    { "pos": "TM", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "TR", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "ML", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "M", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "MR", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "BL", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "BM", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" },
    { "pos": "BR", "name": "Carrot", "asset": "minecraft:carrot", "wikiUrl": "https://minecraft.wiki/w/Carrot" }
  ],
  "output": { "name": "Invisi-carrot", "asset": "mainmod:charm-invisi-carrot", "count": 8 }
}
```
