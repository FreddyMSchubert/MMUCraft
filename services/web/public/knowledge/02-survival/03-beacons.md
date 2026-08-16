====
id: dynamic-beacons
unlockOrder: 5
chatMessage: You've unlocked knowledge on material-based beacon ranges.
sidebarTitle: Beacon Ranges
====

The range of blocks that an activated beacon affects (so the max distance you can be from the beacon while still gaining its effects) has been changed.

It is now based on what blocks the beacon is made out of.

- Iron Block -> +0.3 blocks range
- Emerald Block -> +0.3 blocks range
- Gold Block -> +0.5 blocks range
- Diamond Block -> +2.5 blocks range
- Netherite Block -> +5 blocks range

Since a full 4-layer beacon has 164 blocks below it, if you used only emerald blocks for that, the range would be 50 blocks. If you used netherite blocks however, the range would be 820 blocks (theoretically).

:::warning
For performance reasons, the range is capped at **200 blocks maximum**. The chunk the beacon resides in must also be loaded in the game, otherwise it won't apply its effects.
:::

:::tip
If you right-click a beacon while sneaking, it will tell you how far its range currently extends.
:::

![Beacon demo image](/assets/knowledge/survival/beacons/image.png)
