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
- Slime Detectors can be useful to figure out where slimes commonly spawn.
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
| <img src="/assets/knowledge/items/slime-detector/sd5.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 5th | You are currently stood in a slime chunk. |
| <img src="/assets/knowledge/items/slime-detector/sd4.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 4th | The nearest slime chunk is bordering the chunk you are stood in. |
| <img src="/assets/knowledge/items/slime-detector/sd3.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 3rd | The nearest slime chunk is 2 chunks away. |
| <img src="/assets/knowledge/items/slime-detector/sd2.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 2nd | The nearest slime chunk is 3 chunks away. |
| <img src="/assets/knowledge/items/slime-detector/sd1.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | 1st | The nearest slime chunk is 4 chunks away. |
| <img src="/assets/knowledge/items/slime-detector/sd0.png" width="150px" style="image-rendering: pixelated; image-rendering: crisp-edge;"> | None | The nearest slime chunk is at least 5 chunks away. |


:::note
This is probably unnecessary to know for usage (no need to learn about this if you don't feel like), but if you want to do precise measurements: The detector uses [Manhattan distance](https://en.wikipedia.org/wiki/Taxicab_geometry) to indicate distance (non-euclidian).
:::
