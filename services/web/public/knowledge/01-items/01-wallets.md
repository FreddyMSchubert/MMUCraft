====
id: wallets
unlockOrder: 1
chatMessage: You've unlocked knowledge on wallets, the way to store dabloons.
sidebarTitle: Wallets
====

Dealing with [dabloons](/play/knowledge/money-basics) manually in your inventory can become annoying quite quickly. The bundle is here to solve all your problems: It can hold an unlimited amount of dabloons!

You can get it by **[buying it from the shop](/play/shop/charm-wallet)** or by crafting it like this:

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "String", "asset": "minecraft:string", "wikiUrl": "https://minecraft.wiki/w/String" },
    { "pos": "MR", "name": "Rabbit Hide", "asset": "minecraft:rabbit_hide", "wikiUrl": "https://minecraft.wiki/w/Rabbit_Hide" }
  ],
  "output": { "name": "Wallet", "asset": "mainmod:charm-wallet" }
}
```

You can insert dabloons into the wallet by crafting the wallet and the dabloons you want to insert together:

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "ML", "name": "Wallet", "asset": "mainmod:charm-wallet" },
    { "pos": "M", "name": "Dabloon Coin", "asset": "mainmod:coin-1" },
    { "pos": "MR", "name": "Dabloon Coin", "asset": "mainmod:coin-1" },
    { "pos": "BM", "name": "Dabloon Coin", "asset": "mainmod:coin-1" }
  ],
  "output": { "name": "Wallet", "tooltip": "The coins are added to the wallet's stored balance.", "asset": "mainmod:charm-wallet" }
}
```

You can remove dabloons by putting the wallet alone into the crafting grid:

```recipe
{
  "type": "shapeless",
  "inputs": [
    { "pos": "M", "name": "Wallet", "asset": "mainmod:charm-wallet" }
  ],
  "output": { "name": "Dabloon Coin", "tooltip": "The recipe returns coins from the wallet's stored balance.", "asset": "mainmod:coin-1" }
}
```

When selling, buying or trading on the website, the dabloons you spend or earn will be directly taken from or inserted into your wallet, no need to take them out.

:::tip
You can apply the [Soulbound enchantment](/play/knowledge/soulbound) to wallets to ensure that you never lose any dabloons, even if you die.
:::
