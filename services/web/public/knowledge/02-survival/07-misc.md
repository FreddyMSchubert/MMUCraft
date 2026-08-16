====
id: crafting-changes
unlockOrder: 4
chatMessage: You've unlocked knowledge on the server's changed crafting recipes.
sidebarTitle: Crafting Changes
====

This page lists implemented recipes that differ from vanilla Minecraft.

## Trident

The server adds a Trident recipe. Buried treasure chests supply Trident Shafts, and each Elder Guardian drops one Trident Prong. See [Utility Staves](/play/knowledge/utility-staves) for the related staff recipes.

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

## Recipes awaiting implementation data

The checked-in project does not define changed recipes for Eyes of Ender, End Crystals, Ender Chests, Jukeboxes, or Powered Rails. They are not shown here because an accurate grid cannot be generated without source data.

## Item repair

Combining damaged items in a crafting grid is disabled. Use the systems described in [Enchanting & Anvils](/play/knowledge/enchanting-and-anvils) instead.
