====
id: redstone-remote
unlockOrder: 5
chatMessage: You have unlocked knowledge on the Slime Detector, which helps you find slime chunks.
sidebarTitle: Redstone Remote
gameplayToggle: inventors
tags:
- slime chunk
- slime finder
- chunk detector
tips:
- Use a Redstone Remote to trigger Redstone machinery from far away.
- You can remotely trigger explosions using a Redstone Remote.
- You can use Redstone Remotes to activate Redstone signals from far away, making for great hidden door and other remotely-activated machinery!
====

# Redstone Remote

Redstone remotes allow you to send redstone signals over the air for vast distances.

To create one, craft it like this:

![Redstone Remote recipe](/assets/knowledge/items/redstone-remote/recipe.png)

:::recipe-items
[Blackstone](https://minecraft.wiki/w/Blackstone) (TL, TR, CL, CR, BL, BR) · [Amethyst Shard](https://minecraft.wiki/w/Amethyst_Shard) (TC) · [Redstone Dust](https://minecraft.wiki/w/Redstone_Dust) (C) · [Copper Ingot](https://minecraft.wiki/w/Copper_Ingot) (BC) → Redstone Remote
:::

It can then be paired up with a [calibrated sculk sensor](https://minecraft.wiki/w/Calibrated_Sculk_Sensor) by right-clicking with it on a calibrated sculk-sensor:

![Redstone Remote Getting Paired](/assets/knowledge/items/redstone-remote/pairing.png)

You can now trigger the calibrated sculk sensor from far away, as long as you stay within the server simulation distance. Hooray!

You can see it here, I was far away, yet activated the redstone lamp just with a right click! (I could have gone further away but then you wouldn't have seen anything...)

![Redstone Remote triggering far away redstone lamp](/assets/knowledge/items/redstone-remote/far_away.png)

:::tip
To figure out what the current server simulation distance is, ask the committee.
:::

Sometimes when you pair it though, you may get this error:

![Redstone Remote failing to pair due to non-matching frequencies](/assets/knowledge/items/redstone-remote/failed_match.png)

That is because both the calibrated sculk sensor and the redstone remote work in 1 of 16 different possible frequencies. If the frequency doesn't match, they can't be paired. The frequencies are, in order of increasing strength:

- 0 / Off (No frequency specified)
- 1
- 2
- 3
- 4
- 5
- 6
- 7
- 8
- 9
- 10
- 11
- 12
- 13
- 14
- 15

![Different-frequency calibrated sculk sensors](/assets/knowledge/items/redstone-remote/differently_calibrated_sensors.png)

To set the calibrated sculk sensor to a certain frequency, power the side of it that is marked in pink with amethyst with a redstone signal as strong as you want the frequency to be. So, since a redstone block creates a redstone power of strength 15, placing it right next to the amethyst side of a calibrated sculk sensor will put it into frequency 15.

To set the redstone remote to a certain frequency, hold shift then left click with the remote in hand to decrease the frequency, and press right click to increase it.

Redstone remotes can only pair one calibrated sculk sensor per frequency, but they do not the sculk sensors paired on other frequencies, so you can pair up to 16 different calibrated sculk sensors to one remote.

A calibrated sculk sensor is in frequency 0 / off when it is not powered by redstone at all.

![Redstone Remote with a bunch of frequencies paired](/assets/knowledge/items/redstone-remote/buncha_frequencies.png)

## Triggering a bunch of redstone at once

You may be unhappy about the limitation that you can only trigger 1 remote redstone signal at once. This limitation doesn't exist!

If you power a chest, any redstone remotes in that chest activate and trigger a bunch of different redstone signals all over the place!

It's really that easy.

![Redstone Remote In chest](/assets/knowledge/items/redstone-remote/in_chest.png)

![Redstone Remote In chest with redstone attached](/assets/knowledge/items/redstone-remote/chest_powered.png)

If one was to now step on that pressure plate, the calibrated sculk sensor tied to that remote would get activated - and of course you could add a buncha redstone remotes into that one chest.

## Clearing a redstone remote

To factory-reset a redstone remote and wipe all its data off, just craft it in a crafting table once with no other input:

![Redstone Remote getting cleared](/assets/knowledge/items/redstone-remote/clear.png)

This wipes of all connected calibrated sculk sensors.

## Naming connections

It's super useful to add a bunch of different sensors to one remote so you trigger a bunch of stuff around your base. But it can get confusing after a while only based on the frequency number, so you can also name frequencies.

1. Name an name tag what you want your frequency to be called in an anvil.
2. Put the redstone remote in the frequency you wish to rename.
3. Craft the redstone remote together with the name tag:

![Redstone Remote Name Frequency](/assets/knowledge/items/redstone-remote/name_frequency.png)

## Soulbound

You can apply soulbound to redstone remotes, so even if you die you can still get into your impenetratable secret base only accessible via a secret door that can only be triggered remotely via an already-paired redstone remote.
