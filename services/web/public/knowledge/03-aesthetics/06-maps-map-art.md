====
id: maps-map-art
unlockOrder: 5
chatMessage: You've unlocked knowledge on how to make smaller maps than usual, and how to make them transparent (great for map art).
sidebarTitle: Maps & Map Art
gameplayToggle: imaginative
tags:
- pixel
- 16
- 32
- 64
- 128
- 256
- chunk
- resolution
- square
- transparent
- translucent
- backdrop
- background
tips:
- By crafting together a map with some shears, you can make it smaller, even smaller than usual, up to 16 by 16 blocks.
- When combining a map with any other block and an invisicarrot, any instances of that block on the map will be seethrough.
====

# Maps & Map Art

:::drop-indicator

With map art, you can make absolutely any texture you want and place it in-game. If you combine that with [invisi-carrots](/play/knowledge/invisicarrot), you can even make parts of the map see-through.

![Map Art Result](/assets/knowledge/aesthetics/maps-map-art/result.png)

You can use this to make any signage or art or cool designs or textures you want.

## Smaller Maps than usual

The first big issue with doing map art when playing in Vanilla is that the smallest Vanilla map size is 128x128 blocks aspect ratio. That means if you want to make a small 16x16 texture, you need to build an 8x8 blocks square for each pixel you wish to make, which takes a whole stack of resources. No more!

![Decreasing Map Size](/assets/knowledge/aesthetics/maps-map-art/decreasing_size.png)

Craft together a map with shears to half the size. If you do this three times, you'll eventually get a 16x16 sized map:

![Decreasing Map Size to the Minimum](/assets/knowledge/aesthetics/maps-map-art/decreasing_size_min.png)

To scale them back up, add a paper:

![Increasing Map Size](/assets/knowledge/aesthetics/maps-map-art/increasing_size.png)

Great! Any map size you may want, super simple.

:::warning
Map positioning works relative to chunks, not relative to where you write onto the map from. Please consider this when starting to build your map art and test whether the subject aligns correctly before fully building it.
:::

## Partially transparent maps

This is already possible in vanilla by building over the void with a layer of glass, but it is incredibly annoying. Here's an easier method:

You can specify any block you want to be the invisible block. When you update the map, anywhere that block was used in the region the map captures will be render transparently.

### 1. Specify the block that indicates transparency

![Increasing Map Size](/assets/knowledge/aesthetics/maps-map-art/create_invisible_block.png)

Just craft together the map, an [invisi-carrot](/play/knowledge/invisicarrot) and any block to turn that block into the maps invisible block.

### 2. Build the art & use a map on it

![Built Mapart Example](/assets/knowledge/aesthetics/maps-map-art/built_mapart.png)

:::context
This amazing artistic bunny art was made on last years server around easter by our awesome Social Media Manager Calum. Back then we had to use over 16.000 blocks and build it over the void in the end to get the map art to work. On the new server we can do it with 265 blocks only and anywhere.
:::

To make this I enabled chunks via `F3+G`, then built the mapart using grass blocks for everything but the egg (because earlier we set grass block to be the invisible block). I then right clicked the map while in the chunk to get the visual.

:::note
You may notice the map art isn't upright, this is not a problem in my case though because I want it to be placed in the world rather than held.
:::

### 3. Place it & make it transparent

You can then place the mapart in an item frame. By default the transparent parts of the map will show the item frame, but by using an [invisicarrot](/play/knowledge/invisicarrot) to make the item frame invisible, you can stop this from happening:

![Map Art Result](/assets/knowledge/aesthetics/maps-map-art/result.png)
