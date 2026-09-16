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

# Slime Detector

![Slime detector in use](/assets/knowledge/items/slime-detector/in-use.png)

The Slime Detector shows how close you are to a slime chunk. This is a great Vanilla way of finding slimes without relying on chunkbase.

## How to get it
You can either buy it from [the shop](https://mmuminecraftsociety.co.uk/play/shop/charm-slime-detector), or you can craft it with one compass and one slime ball.

![Recipe](/assets/knowledge/items/slime-detector/recipe.png)

:::recipe-items
[Compass](https://minecraft.wiki/w/Compass) + [Slime Ball](https://minecraft.wiki/w/Slimeball) → Slime Detector
:::

## How to use it

Hold the detector and right-click to take a measurement.

The amount of active bars indicate how close you are to a slime chunk.

| | Active bar | Distance to the nearest slime chunk |
| - | - | - |
| <![5th bar](/assets/knowledge/items/slime-detector/sd5.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 5th | Your current chunk is a slime chunk. |
| <![4th bar](/assets/knowledge/items/slime-detector/sd4.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 4th | Nearest slime chunk is 1 chunk away. |
| <![3rd bar](/assets/knowledge/items/slime-detector/sd3.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 3rd | Nearest slime chunk is 2 chunks away. |
| <![2nd bar](/assets/knowledge/items/slime-detector/sd2.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 2nd | Nearest slime chunk is 3 chunks away. |
| <![1st bar](/assets/knowledge/items/slime-detector/sd1.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 1st | Nearest slime chunk is 4 chunks away. |
| <![No bars](/assets/knowledge/items/slime-detector/sd0.png)  width="150" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | None | Nearest slime chunk is at least 5 chunks away. |


:::note
This is probably unnecessary to know for usage (no need to learn about this if you don't feel like), but if you want to do precise measurements: The detector uses [Manhattan distance](https://en.wikipedia.org/wiki/Taxicab_geometry) to indicate distance (non-euclidian).
:::
