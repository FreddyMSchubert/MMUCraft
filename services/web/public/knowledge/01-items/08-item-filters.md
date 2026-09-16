====
id: item-filters
unlockOrder: 5
chatMessage: You've unlocked knowledge on item filters, which are great for things like sorting systems.
sidebarTitle: Item Filters
gameplayToggle: inventors
tags:
- sort
- sorting
- system
- hopper
- copper
- order
- check
tips:
- Item Filters configure what items a hopper lets through, which makes fully automatic sorting systems super simple to set up.
- Item filters are not only useful for sorting systems, but also for seperating the different items a farm outputs into seperate chests.
- Item filters allow you to easily specify whole groups of items (e.g. all oak woodset items), rather than needing to add each item individually.
====

# Item filters / Hopper Filters

Can be made as follows:

![Item filter recipe](/assets/knowledge/items/item-filters/recipe.png)

:::recipe-items
[Paper](https://minecraft.wiki/w/Paper) · [Copper Grate](https://minecraft.wiki/w/Copper_Grate) → Item filter
:::

## Video

Here's a video, if you don't feel like reading the rest of this page. The content is the same:

<iframe width="560" height="315" src="https://www.youtube.com/embed/yu8jYpu6lrY?si=3U4gmtBjaqoMvaxo" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>

You can find a list of all groups here: https://github.com/FreddyMSchubert/MMUCraft/tree/main/minecraft/main/data/data/hopper_filter_groups

## How to use item filters

When placed in a hopper, an item file stops the hopper from sucking in certain items. For example:

![Mini Storage System Visual](/assets/knowledge/items/item-filters/mini-storage-system.png)

When you place an item filter that has been configured to only allow cobblestone into the chest marked with a red arrow and put a variety of items, including cobblestone, into the chest with the blue arrow, then all the cobblestone would end up in the chest on the right (green arrow), and everything else would end up in the chest on the left (purple arrow).

## How to set up such an item filter

Item filters have two lists, a whitelist and a blacklist.

- Items added to the whitelist are allowed.
- Items added to the blacklist are not allowed, even if they are on the whitelist.

To add something to the item filters blacklist or whitelist, you must first put the item filter into blacklist or whitelist mode.

To set the mode of an item filter, put it into the crafting table with no other items, and craft it once. This cycles what mode the item filter is in. Here, an item filter that was in Blacklist Group mode gets turned set to Whitelist Single mode.

![Switching item filter groups visual](/assets/knowledge/items/item-filters/switching_groups.png)

## How to add items to the item filter

### Single Mode

To add single items to the filter (make sure the mode is set to the right thing before hand), craft the filter together with the items of your choice:

![Filtering for single items recipe](/assets/knowledge/items/item-filters/adding_items_to_filter.png)

Yes, you can even add multiple items at once, which does the same thing as adding them one by one. The items stay behind after you add them to the filter, so you don't lose any materials.

The filter shown there would now allow buckets and water buckets and nothing else.

### Group Mode

This is great, but can get super annoying. Say you want to make a chest for the oak woodset and all its derivatives (oak doors, boats, shelves, ...). Using single mode, you'd have to make each item once and add it to the filter manually to get this to work. Very cucumbersome!

Instead, item filters come with groups, which you are likely to want to sort things in. To make an item filter allow or disallow a group, first cycle the item filter mode to either Whitelist group mode or Blacklist group mode. Then, add an item that is in the group you wish your item filter to filter for or against, and craft it together with the filter:

![Filtering for groups recipe](/assets/knowledge/items/item-filters/filtering_for_groups.png)

Now, this filter will allow all of the items and blocks that are in the oak woodset! Oak buttons, trapdoors, fence gates, chest boats, ..., and it barely took any setup!

You can find a list of all groups here: https://github.com/FreddyMSchubert/MMUCraft/tree/main/minecraft/main/data/data/hopper_filter_groups

If you have suggestions for new groups to add, we are happy to add more groups.

#### Getting the group you want

![Filtering for groups recipe gone wrong](/assets/knowledge/items/item-filters/wrong_group_recipe.png)

One problem with item filter groups is that items may be in multiple groups. So if you craft the item together with a filter in a group mode, a random group will be picked for the filter. You can see this in the image at the top - planks is a totally valid group and someone else may want to create a planks chest, but were looking for an oak chest.

To avoid this and get the item filter to look for the specific group you want, you can do either of these two things:

1. Cycle the randomness. If you pick up one of the items in the recipe and place it back into the crafting grid, the random pick will refresh, and eventually land on the group you are looking for.
2. Add more items or blocks that are in the group you want. This will make the search more specific, because only groups that have all specified items will be picked from:

![Filtering for groups recipe gone right again cause more specific](/assets/knowledge/items/item-filters/more_specific_groups.png)

## Advanced Item Filters

![Advacned item filtering](/assets/knowledge/items/item-filters/advanced_filter.png)

You can use the blacklist to make for more complex item filtering combinations. This filter for example would let through all the oak wood set blocks and items EXCEPT for oak logs. This could be great if you have tons of oak logs so you want to seperate the oak logs only into a different chest.

You can add as many elements as you want to item filters, but at some point the tooltip might go offscreen lol

## Clearing item filters

To clear an item filter, right click it onto a water-filled cauldron:

![item filter clearing](/assets/knowledge/items/item-filters/clearing.png)
