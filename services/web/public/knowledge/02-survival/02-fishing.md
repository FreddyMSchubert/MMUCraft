====
id: fishing
unlockOrder: 4
chatMessage: You've unlocked knowledge on the server's fishing system.
sidebarTitle: Fishing
====

The fishing system adds bait, magnets, clovers, custom fish, and custom food.

## Golden fishing supplies

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "TM", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "TR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "ML", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "M", "name": "Worm", "asset": "mainmod:worms" },
    { "pos": "MR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BL", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BM", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" }
  ],
  "output": { "name": "Golden Worm", "asset": "mainmod:golden-worms" }
}
```

```recipe
{
  "type": "shaped",
  "inputs": [
    { "pos": "TL", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "TM", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "TR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "ML", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "M", "name": "Item Magnet", "asset": "mainmod:item-magnet" },
    { "pos": "MR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BL", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BM", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" },
    { "pos": "BR", "name": "Gold Ingot", "asset": "minecraft:gold_ingot", "wikiUrl": "https://minecraft.wiki/w/Gold_Ingot" }
  ],
  "output": { "name": "Golden Item Magnet", "asset": "mainmod:golden-item-magnet" }
}
```

## Clovers

Combine clovers in any arrangement. These are the four valid combinations for a Four Leaf Clover.

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "TL", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "TM", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "ML", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "M", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" }
], "output": { "name": "Four Leaf Clover", "asset": "mainmod:4-leaf-clover" } }
```

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "TL", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "TM", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "M", "name": "Two Leaf Clover", "asset": "mainmod:2-leaf-clover" }
], "output": { "name": "Four Leaf Clover", "asset": "mainmod:4-leaf-clover" } }
```

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "M", "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover" },
  { "pos": "MR", "name": "Three Leaf Clover", "asset": "mainmod:3-leaf-clover" }
], "output": { "name": "Four Leaf Clover", "asset": "mainmod:4-leaf-clover" } }
```

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "M", "name": "Two Leaf Clover", "asset": "mainmod:2-leaf-clover" },
  { "pos": "MR", "name": "Two Leaf Clover", "asset": "mainmod:2-leaf-clover" }
], "output": { "name": "Four Leaf Clover", "asset": "mainmod:4-leaf-clover" } }
```

You can also split larger clovers back into One Leaf Clovers.

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "M", "name": "Two Leaf Clover", "asset": "mainmod:2-leaf-clover" }
], "output": { "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover", "count": 2 } }
```

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "M", "name": "Three Leaf Clover", "asset": "mainmod:3-leaf-clover" }
], "output": { "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover", "count": 3 } }
```

```recipe
{ "type": "shapeless", "inputs": [
  { "pos": "M", "name": "Four Leaf Clover", "asset": "mainmod:4-leaf-clover" }
], "output": { "name": "One Leaf Clover", "asset": "mainmod:1-leaf-clover", "count": 4 } }
```

The [Lucky Charm](/play/knowledge/charms) and Leprechaun Boots also affect luck.

## Fish and food

Cook any custom fish in a furnace. The result depends on the fish. These representative inputs and their exact outputs cycle together.

```recipe
{
  "type": "shapeless",
  "inputs": [{
    "pos": "M",
    "name": "Custom Fish",
    "tooltip": "Cook this item in a furnace.",
    "asset": [
      { "src": "mainmod:fish-tuna", "title": "Tuna" },
      { "src": "mainmod:fish-walleye", "title": "Walleye" },
      { "src": "mainmod:fish-lobster", "title": "Lobster" },
      { "src": "mainmod:fish-octopus", "title": "Octopus" }
    ]
  }],
  "output": {
    "name": "Cooked Fish",
    "asset": [
      { "src": "mainmod:cooked-red-fish", "title": "Cooked Red Fish" },
      { "src": "mainmod:cooked-white-fish", "title": "Cooked White Fish" },
      { "src": "mainmod:crab-claw", "title": "Crab Claw" },
      { "src": "mainmod:tentacle", "title": "Tentacle" }
    ]
  }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Any Custom Fish", "tooltip": "Any item in the server's fishes tag works.", "asset": "mainmod:fish-walleye" },
    { "pos": "MR", "name": "Kelp", "asset": "minecraft:kelp", "wikiUrl": "https://minecraft.wiki/w/Kelp" }
  ],
  "output": { "name": "Sushi", "asset": "mainmod:sushi" }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Cookie", "asset": "minecraft:cookie", "wikiUrl": "https://minecraft.wiki/w/Cookie" },
    { "pos": "TM", "name": "Beetroot", "asset": "minecraft:beetroot", "wikiUrl": "https://minecraft.wiki/w/Beetroot" },
    { "pos": "TR", "name": "Pumpkin", "asset": "minecraft:pumpkin", "wikiUrl": "https://minecraft.wiki/w/Pumpkin" },
    { "pos": "ML", "name": "Gold Nugget", "asset": "minecraft:gold_nugget", "wikiUrl": "https://minecraft.wiki/w/Gold_Nugget" },
    { "pos": "M", "name": "Bowl", "asset": "minecraft:bowl", "wikiUrl": "https://minecraft.wiki/w/Bowl" },
    { "pos": "MR", "name": "Any Custom Fish", "tooltip": "Any item in the server's fishes tag works.", "asset": "mainmod:fish-walleye" },
    { "pos": "BL", "name": "Any Mushroom", "asset": "minecraft:red_mushroom", "wikiUrl": "https://minecraft.wiki/w/Mushroom" },
    { "pos": "BM", "name": "Any Fungus", "asset": "minecraft:crimson_fungus", "wikiUrl": "https://minecraft.wiki/w/Fungus" },
    { "pos": "BR", "name": "Raw Farm-animal Meat", "asset": "minecraft:beef", "wikiUrl": "https://minecraft.wiki/w/Raw_Beef" }
  ],
  "output": { "name": "Golden Nutritional Paste", "asset": "mainmod:golden-nutritional-paste" }
}
```
