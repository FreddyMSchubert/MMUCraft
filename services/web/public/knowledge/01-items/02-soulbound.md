====
id: soulbound
unlockOrder: 2
chatMessage: You've unlocked knowledge on the Soulbound enchantment, a method to never lose certain items - even if you die.
sidebarTitle: Soulbound Enchantment
====

An item enchanted with ***SOULBOUND*** will attach to your soul, not your physical form. This allows it to stay affixed to your innermost being as it travels the afterlife in search of a new body to inhabit.

That is to say, you won't lose ***SOULBOUND*** items under the server's [death and respawning rules](/play/knowledge/death-and-respawning). They'll stay in your inventory.

:::tip
***SOULBOUND*** can be applied to all armor and tools, but also to [*wallets*](/play/knowledge/wallets) and [*recovery compasses*](/play/knowledge/death-and-respawning). Those latter two especially are **supremely useful**.
:::

:::note
The ***SOULBOUND*** enchantment is not compatible with Mending, similar to how you can't have Infinity and Mending on the same bow.
:::

## Obtaining

***SOULBOUND*** books can be crafted like this:

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Soul", "asset": "mainmod:soul" },
    { "pos": "TM", "name": "Book", "asset": "minecraft:book", "wikiUrl": "https://minecraft.wiki/w/Book" },
    { "pos": "TR", "name": "Block of Lapis Lazuli", "asset": "minecraft:lapis_block", "wikiUrl": "https://minecraft.wiki/w/Block_of_Lapis_Lazuli" },
    { "pos": "ML", "name": "Amethyst Shard", "asset": "minecraft:amethyst_shard", "wikiUrl": "https://minecraft.wiki/w/Amethyst_Shard" },
    { "pos": "M", "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand" },
    { "pos": "MR", "name": "Soul Soil", "asset": "minecraft:soul_soil", "wikiUrl": "https://minecraft.wiki/w/Soul_Soil" }
  ],
  "output": { "name": "Soulbound Enchanted Book", "asset": "minecraft:enchanted_book" }
}
```

You may notice that it requires a mysterious blue flame to craft. This is a ***SOUL***. It is dropped by players when they die.

:::note
*It is left to the discretion and creativity of the reader to obtain souls in a morally unobjectionable manner.*
:::

![Souls Example Image](/assets/knowledge/items/soulbound/soul.png)

If you are wondering if it's possible to obtain ***SOULBOUND*** if you haven't ventured into the Nether yet, it very much is. You can obtain the ***SOUL***sand and ***SOUL***soil required to craft the book using these recipes:

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "TM", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "TR", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "ML", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "M", "name": "Soul", "asset": "mainmod:soul" },
    { "pos": "MR", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "BL", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "BM", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" },
    { "pos": "BR", "name": "Dirt", "asset": "minecraft:dirt", "wikiUrl": "https://minecraft.wiki/w/Dirt" }
  ],
  "output": { "name": "Soul Soil", "asset": "minecraft:soul_soil", "wikiUrl": "https://minecraft.wiki/w/Soul_Soil", "count": 8 }
}
```

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "TL", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "TM", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "TR", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "ML", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "M", "name": "Soul", "asset": "mainmod:soul" },
    { "pos": "MR", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "BL", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "BM", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" },
    { "pos": "BR", "name": "Sand", "asset": "minecraft:sand", "wikiUrl": "https://minecraft.wiki/w/Sand" }
  ],
  "output": { "name": "Soul Sand", "asset": "minecraft:soul_sand", "wikiUrl": "https://minecraft.wiki/w/Soul_Sand", "count": 8 }
}
```
