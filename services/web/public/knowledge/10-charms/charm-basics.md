====
id: charm_basics
unlockOrder: 0
chatMessage: You've unlocked the basics of charms. Visit the website to learn more.
sidebarTitle: Charm Basics
====

## Charm Basics

Charms are custom server-side items. They usually look like normal Minecraft items, but they have extra behaviour handled by the server mod.

A charm can be passive, active, held, equipped, or consumable. For example, one charm may work every tick while worn in the correct equipment slot, while another may only activate when you right-click with it in your hand.

- **Held charms** usually activate from your main hand or off hand.
- **Equippable charms** usually care about an armour slot.
- **Consumable charms** behave like food, potions, or charged-use items.
- **Broken charms** may still exist as items, but their level is too low to provide an effect.

The server tracks charm data directly on the item stack. That means two visually similar items can behave differently if their stored charm data is different.

