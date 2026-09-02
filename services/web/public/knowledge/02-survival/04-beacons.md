====
id: dynamic-beacons
unlockOrder: 5
chatMessage: You've unlocked knowledge on material-based beacon ranges.
sidebarTitle: Beacon Ranges
tags:
- becaons
- becon range
tips:
- The range in which a beacon spreads its effects is determined by what mineral blocks are placed under it. The more valuable the block, the farther it extends the beacons range.
- Using Diamond Blocks to power a Beacon will increase the Beacons range drastically, way more than the normal Vanilla maximum Beacon range.
====

The range of blocks that an activated beacon affects (so the max distance you can be from the beacon while still gaining its effects) has been changed.

It is now based on what blocks the beacon is made out of.

- Iron Block -> +0.3 blocks range
- Emerald Block -> +0.3 blocks range
- Gold Block -> +0.5 blocks range
- Diamond Block -> +2.5 blocks range
- Netherite Block -> +5 blocks range

This may initially sound like it isn't a lot - but actually, even when only using Emerald Blocks which extend the beacon range the least, the beacon range of the full beacon will end up being the same as the maximum range of a normal Vanilla beacon. Therefore, this is a **purely positive change** which is great for high-range beacon effects.

Since a full 4-layer beacon has 164 blocks below it, if you used only emerald blocks for that, the range would be 50 blocks. If you used netherite blocks however, the range would be 820 blocks (theoretically).

:::warning
For performance reasons, the range is capped at **200 blocks maximum**. The chunk the beacon resides in must also be loaded in the game, otherwise it won't apply its effects.
:::

:::tip
If you right-click a beacon while sneaking, it will tell you how far its range currently extends:
:::

![Beacon demo image](/assets/knowledge/survival/beacons/image.png)
