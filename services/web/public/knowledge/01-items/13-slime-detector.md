====
id: slime-detector
unlockOrder: 5
chatMessage: You have unlocked knowledge on the Slime Detector, which helps you find slime chunks.
sidebarTitle: Slime Detector
gameplayToggle: welcoming
tags:
- slime chunk
- slime finder
- chunk detector
tips:
- Use the Slime Detector before you dig a slime farm.
- A diagonal chunk is two chunks away because the detector uses Manhattan distance.
- The detector does not load chunks. It checks chunk coordinates only when you use it.
====

The Slime Detector shows how close you are to a slime chunk. This is a great Vanilla way of finding slimes without relying on chunkbase.

## How to make it

Combine one compass and one slime ball in a crafting table.

:::recipe-items
[Compass](https://minecraft.wiki/w/Compass) + [Slime Ball](https://minecraft.wiki/w/Slimeball) → Slime Detector
:::

## How to use it

Hold the detector and right-click to take a measurement.

The amount of active bars indicate how close you are to a slime chunk.

| Active bars | Distance to the nearest slime chunk |
| - | - |
| 5 | Your current chunk is a slime chunk. |
| 4 | Nearest slime chunk is 1 chunk away. |
| 3 | Nearest slime chunk is 2 chunks away. |
| 2 | Nearest slime chunk is 3 chunks away. |
| 1 | Nearest slime chunk is 4 chunks away. |
| 0 | Nearest slime chunk is 5 or more chunks away. |

:::note
This is probably unnecessary to know for usage (no need to learn about this if you don't feel like), but if you want to do precise measurements: The detector uses [Manhattan distance](https://en.wikipedia.org/wiki/Taxicab_geometry) to indicate distance (non-euclidian).
:::
