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

The Slime Detector shows how close you are to a slime chunk.

## How to make it

Combine one compass and one slime ball. The recipe is shapeless, so you can put the two items in any crafting slots.

:::recipe-items
[Compass](https://minecraft.wiki/w/Compass) + [Slime Ball](https://minecraft.wiki/w/Slimeball) → Slime Detector
:::

## How to use it

Hold the detector and right-click. The bars move while the detector scans. After 0.5 to 2 seconds, the detector shows the result.

The detector keeps all five bars visible. An active bar has a bright color. An inactive bar is gray.

| Active bars | Distance to the nearest slime chunk |
| - | - |
| 5 | Your current chunk is a slime chunk. |
| 4 | 1 chunk |
| 3 | 2 chunks |
| 2 | 3 chunks |
| 1 | 4 chunks |
| 0 | More than 4 chunks |

The detector uses Manhattan distance. It adds the east-west distance and the north-south distance. For example, a chunk that is one chunk east and one chunk north has a distance of two.
