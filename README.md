# KubeCraft

> _Toto, I've a feeling we're not in Kansas anymore._

## Dev

1. Make sure k3d & kubectl, Docker, tilt are installed.
2. Duplicate .env.example files in each service & fill in actual values.
3. To start local k3d instance: `k3d cluster create mc-dev --registry-create mc-dev-registry`. To clean up: `k3d cluster delete mc-dev`.
4. Run `tilt up` to get started.

Tilt will now restart the minecraft pod automatically whenever the mod is built.

- To access the server console: `kubectl attach -n mc-stack-dev -it deploy/minecraft`
- To allow people to join server: `kubectl -n mc-stack-dev port-forward --address 0.0.0.0 svc/minecraft 25565:25565`

## The goal

One main, largely vanilla survival server. Main server.

Every saturday between 4pm to 12pm GMT+0, automatically swapped out for a surprising saturday server, players get redirected. Surprising saturday server runs custom mods that mix things up.
At midnight, everybody gets redirected back to main server, the winner gets announced in chat, and the winner receives a unique custom cosmetic (particle trail, new ability like wearing any item as hat, ...) via custom server-side mod.

By typing a command in chat, you can challenge other players to a minigame, such as dueling, minecraft gartic phone or master builders.

Making custom minecraft content isn't that hard with a fabric mod, so by adding more minigames and a new one-time never-again surprising saturday every week, time required stays relatively low while hopefully keeping interest for longer through a built-in novelty mechanism.

## How

Minecraft server setup
- [K8s](https://kubernetes.io/) will maintain all the following pods:
- **Server pods**: the currently running server (main or surprising saturday) and dynamically spin up one-off minigame worlds
  - each pod will use a fabric server docker image with the mods already built in, built with pipelines from this repo
  - k8s cron to automatically switch out main for saturday server saturdays 
- [Velocity](https://papermc.io/software/velocity/) Minecraft Server Proxy will connect players to currently active server pod
- [RabbitMQ](https://www.rabbitmq.com/) will handle communication between k8s pods & ScoreKeeper (winners etc)
- [nginx](https://nginx.org/) for a tiny web server that enables minigame world downloads & serves cosmetics website
- **ScoreKeeper/website** - Some centralized custom program that will
  - determines which mc account based on discord sign-in, then you can buy cosmetics with the in-game money.
  - handles scoring while surprising saturday is running
  - maintain inter-server player stats (will be called by main pod mod for permission checks)
  - delete nginx-served world backups after some time
  - maintain which cosmetics are owned by whom
  - maybe even auto-update the server resource pack
  - anything else we need
- This repo will host the dynamic enforced server side resource pack, allowing for cosmetics to be rewarded to surprising saturday winners

Setup will not be elastic and everything will be on one big VPS cause we don't have the budget for more.

The surprising saturday & minigame world custom mods will be developed in this repo & then the surprising saturday docker image will be updated each week.

## To do list / priority order

- [ ] Get a basic docker image for vanilla server set up
- [ ] Make k8s pull & run it and restart it when its down
- [ ] Velocity container that forwards to the main server
- [ ] Server auth & authenticating with discord (from minecraft generate a url that encodes username, on website you can sign in with discord & input mmu email and input a code from that email)
- [ ] Setup rabbitmq that sends data between scorekeeper and main server mod. every day at 9, scorekeeper initiates border expansion.
- [ ] On website, add shop tab. Create a few placeholder cosmetics. When bought, send rabbitmq message to mod to unlock that cosmetic for player / give player the item.

## Surprising Saturday Ideas

All very unordered, potentially bad, some probably too hard / unfun, whatever - idea collection.

- achievements who can get the most
- vegan run. eating meat, throwing eggs, killing any mob or player kills you immediately from moral decay. Kill dragon via accidental death by unfortunate bed placement. Increase tick speed slightly, make leaves drop more apples, make nether chests have more blaze rods.
- bloodlust challenge. you cant eat, killing things satisfyes your hunger.
- zombie challenge - being in sun burns you
- randomized mob challenge - everybody spawns in as a random mob, find each other
- no block placing
- half a heart with keepinv and respawn in range of death
- run with insane shader
- start in nether
- first death to void wins
- start in end islands (stone recipes work with end stone, you can craft wood from chorus)
- every chunk is random
- skyblock
- https://modrinth.com/mod/hexlands
- insanely limited world size
- https://modrinth.com/datapack/chunklock
- collect every type of flower
- perma-night
- a totally normal vanilla world (herobrine) -> https://modrinth.com/datapack/from-the-fog https://www.curseforge.com/minecraft/mc-mods/sever-side-horror https://www.curseforge.com/minecraft/texture-packs/true-darkness
- one block
- summon herobrine
- https://modrinth.com/mod/circumnavigate
- the floor is lava - every block that is below the sun has a 1 in 6 chance not to turn to lava
- no inventory, only hotbar
- being in rain or water damages you
- insane mob ai mod, https://modrinth.com/mod/improved-mobs
- upside down end gen
- parcour civilization world gen
- golden age minecraft recipes & world gen & nether
- sky islands world gen
- lucky block flat world - each block broken gives you a 1 in 100 chance to win the challenge
- https://modrinth.com/mod/gwg
- cave world world gen
- superflat world gen
- amplified world gen
- you can only breathe when touching water or snow (even in cauldron), defeat ender dragon
- the purge. played on copy of survival server, but anything done is reset at midnight. kill the most to win
- lifesteal
- catch as many unique fish as possible
- hardcore
- every half an hour another players pos is revealed in chat. person who kills them gets a point. most points wins
- who can get the most of item x in their ender chest (e.g. watermelons)
- hide and seek. special retextured blocks that give a point when broken hidden around map. every 10 minutes you get your relative direction to the nearest one
- hide and seek - start a game with command, players get a compass pointing to you when outside a certain distance to you. if they dont find you within a certain time you get a point, otherwise winner gets a point. need some way to regulate people going underground.
- most unique effects applied at once
- most unique mobs killed (kill each mob once)
- most wardens killed
- most unique death messages (same message a second time doesnt count)
- most sniffers bread
- speedrun, quickest x e.g. ender dragon death, wind charged 3 book
- one hit wither with mace
- longest online time between deaths, everyone can see each others positions, small world border
- randomised block / mob loot tables
- randomised crafting recipes
- everyone shares a synced inventory trying to kill the dragon
- every like 2 minutes your location is swapped with another player's
- you can only break oak logs, stone, coal ore, iron ore, diamond ore, end stone, gravel, cactus; you can only place crafting table, furnace, obsidian, torch, oxidised copper stairs, TNT, oak boats, green beds. defeat dragon
- monster hunter alphabet challenge - kill mobs in alphabetical order
- fetchr / bingo full or only one line - maybe as gamemode
- stack of block sin alphabetical order - add something for X + U + V, also kelp block, enable mob drop heads for z Zombie

With some of these multiple players could get them at the same time, so we should in the ScoreKeeper just take record whenever theres a clear winner in the current result state, even if another player also wins a millisecond later, thus making winning together essentially randomize the winner.

Might be nice to make some challenges have multiple winners to encourage collaboration.

also heres some youtubers to take inspo from:

- https://www.youtube.com/@Suuuperbro0

## Dueling

Duel Types

- Underwater
- Lightning item to strike each other with lightning, small cooldown to react
- Jousting with spears on horses
- Mounted PVP
- Deathrun
- swamp arena
- cliff arena, everyone has jump boost
- elytra
- mace, everyone jump boost
- underground, mine to opponents
- waves of mobs, who survives longest / kills most mobs

## Economy

### Money gains

- Money from winning challenges
	- Depending on challenge you get different amounts, there may be participation, everybody that reaches goal may get money, whatever. We can make it up as we go.
- Members get money after becoming members, everybody gets a tiny bit of money when joining initially
- Bet money on minigame outcome e.g. battling
	- people putting in money for themselves
	- betting on someone else via website
- Daily challenges - Every day theres an item you can deposit for some cash
	- 7 golden hoes
	- glow berries
	- stack poisonous potatoes
	- potions of slow falling / oozing / weaving / infestation
	- copper horse armor
	- brown dyed leather horse armor
	- signed book
	- grey harness
	- enchanted wooden shovel (mending)
	- danger pottery sherd, or general ones
	- turtle scute & eggs
	- brush with durability 1
	- carrot on a stick with less than half durability
	- copper nautilus armor
	- bee nest with bees inside
	- stack of end rods
	- chiseled red sandstone
	- minekart with furnace
	- 31 red sandstone stairs
	- blue eggs / brown eggs
	- 2 cyan carpet
	- sniffer flower
	- 64 block of raw copper
	- ominous banner
	- waxed weathered chiseled copper
	- cookies
	- wither roses
	- sharpness 2
	- bucket of tadpole
	- glow item frame
	- shroom lights
	- dripstone

### Physical money

Money items: 1 - 5 - 10 - 50 - 100 - 500 - 1000 - 5000 - 10000 - 50000 - 100000 - 500000 - 1000000

Value of money roughly matching https://www.naughtynathan.co.uk/minecraft/prices.htm

Do a code-based recipe that calculates the money together.

For extraction, the recipe always turns
- 5* to 5x1*
- 1* to 2x5*

`/withdraw` & `/deposit` commands - withdraw takes a specified amount of money and gives it to player as item, deposit puts current stack into bank

## Website

- Cosmetics Shop
- View currently running duels, bet money on them
- duel history
- view countdown until next surprising saturday
- display whos currently online
- tutorial of important commands
- overview of commitee members

## Server interest over time

Day 1 the server border is 1000x1000

Every day on the server forever, its scheduled, the border increases by 300 blocks

- After Third of semester 1: unlock nether
- After second third of semester 1: Remove world border restriction (potion of displacement still tps within old area), potion of far displacement costs more but teleports you outside of border as well. takes longer to drink.

- First week of semester 2: unlock end

## Cosmetics Ideas

we could also make a blueprint item that you need to find in the world which will unlock the cosmetic in the shop

we could also do sale vouchers where you have a 20% discount on the item you buy while you have it in the inventory or something

- snorkle - more water breathing
- villager hat - reduced trade cost
- more looting armor
- more mount speed hat
- anglers hat - more luck of the sea
- 4 leaf clover - increased fortune when held
- longer invincibility frames
- get speed effect after damage
- chance to light attackers on fire
- antidote vessel - decreases harmful effect duration
- quiver - gives you infinity
- horse shoes - increase mounted step height
- thick socks - walk over powdered snow
- frost walker works in boats
- veinminer, blocked to a max of a few blocks, increased per level
- fall damage cant kill you only set to 1 hp
- subnautica underwater ascend item
- illness totem - slowly produce slimeballs
- fire stone - get no fire damage but deal extra damage while on fire
- light stone - higher attack above a certain light level
- darkness stone - higher attack below a certain light level
- rain stone - higher damage when wet
- warmth stone - higher damage in warm areas (warm biomes, nether)
- cold stone - higher damage in cold areas (cold biomes, end)
- depth stone - higher damage when below y=0 or in nether
- instead of dying, your levels can be exported into hp when you would die. e.g. 3 levels per half a heart
- increased critical damage
- more arrow damage / velocity
- mending for hp
- regenerate hp slowly when standing still and looking down
- deepslate mining speed increase (to instamine with efficiency 5)
- endstone mining speed increase
- mace-like armor penetration for any weapon
- higher boat speed
- higher damage while under a negative effect
- set max hp to 1, insane damage
- stores xp when held, releases xp when right clicked. (up to a certain amount)
- adrenaline - killing mobs boost speed + attack speed for a little bit
- more damage the less hunger bars
- backstab - more damage when hitting from behind
- headshots more damage
- the deeper you are the faster you mine
- wall jump
- reducing fall speed & reset fall damage calculation while touching a wall
- mined stuff gets smelted
- slime boots - fall damage partially converts into upward bounce
- damage taken gets stored - next hit deals more damage

- zeus bolt - cooldown 2 hours - summons lightning at target pos
- poseidons trident - when thrown, drags people underwater super quickly
- hades grace - keep your items after death

For more inspiration:
- https://minecraft.wiki/w/Attribute#Armor_toughness
- https://minecraft.wiki/w/Enchanting
- https://minecraft.wiki/w/Effect

### cosmetics without functional effect

these arent mit licensed but we can just get some inspiration

- https://modrinth.com/resourcepack/many-hats
- https://modrinth.com/mod/animal-hats-yeah
- https://www.curseforge.com/minecraft/texture-packs/somies-variable-hats

also manual ideas:

- villager nose
- witch nose (with dyeable pimple)
- kitty ears
- pile of coins
- witch hat from vanilla
- different hair types
- rainbow clown hair
- banana peel
- antenna
- bike / motorbike helmet
- pirate hat with skull
- light bulb
- candle
- this meme, sad and happy version -> https://media.tenor.com/cI1t4CvrgogAAAAe/crying-under-mask-feels.png
- alien head
- top hat
- goop head
- viking helmet
- sombrero
- cowboy hat
- animals (sitting on hat, or animal shaped hat)
- duck hat
- steampunk hat
- tricorn thing oderzo made
- party cone hat
- santa hat
- headphones
- lever
- dragon head
- armor / knights
- different glasses / shades
- different pumpkin heads
- minion eyes
- green leprechaun hat thing
- mario / luigi hat
- night cap
- bull horns
- mario-toad-like mushroom cap
- fnaf stuff, springtrap mask
- masks from majoras mask
- beards
- kings crown
- upper half of a shulker fit on a head
- turtle shell on head
- antlers
- unicorn horn
- lying down cat / dog / fox / frog / bee
- cute new chicken
- lil octopusses
- ghast hat so it looks your face is the ghasts face. fully engulfs your head but hole in front
- rod from copper golems
- flower on head
- flower crown
- bow
- gas mask

## Fishing Changes

More Fish, more junk, more treasure
Different Fish Loottables for Warm, Cold, Temperate, End

Advancements
- Catch all
- Catch a common / uncommon / rare / epic / legendary / mythical
- Every non-common fish can have an advancement

Cool stuff to fish up
- Trident Prongue -> craft a trident with it (trident prongue, trident hilt dropped by elder guardian)
- Kranken tentacle / beak (damages) / eye
- Megalodon tooth
- Unrecognizable megalodon victim remains
- Loch Ness Monster Remains - oak log
- Driftwood
- Half-broken sword (called murder weapon)
- ancient battle axe
- message in a bottle

### Potentially

Completion Book with all fish

### Good mit-licensed mods to take some nice fish textures from

- https://modrinth.com/mod/gone-fishing
- https://modrinth.com/mod/fishingfrenzy
- https://modrinth.com/mod/fishery
- https://modrinth.com/mod/exlines-fishing
- https://modrinth.com/datapack/fishingsim
- https://modrinth.com/mod/fishing-101
- https://modrinth.com/mod/fish-of-thieves

### and to make fishing in other places work

- https://modrinth.com/datapack/gm4-end-fishing
- https://modrinth.com/datapack/lava-fishing

hopefully with that i can figure it out

## Progression Changes

### Enchanting

villagers dont drop enchanted books anymore. instead, get them from structures at increased reliable chances but only ever at the lowest level.
you can put enchanted books on lecterns, and the corresponding librarian will offer that trade at high price but reset anvil cost, but will add a random curse to it. This can be removed with the cursebreaker tome.
repairing items in an anvil with another item does not remove the enchantments, just keeps the first items enchantments. you can repair normally with materials and also repair netherite stuff with diamonds. repairing does not increase xp cost.
combining books has changed, now a sharpness 1 + a sharpness 2 book makes a sharpness 3 book.

| Enchantment | Initial book obtaining | Item needed to trade instead of book |
| - | - | - |
| Looting | Nether Fortress | funny bone dropped by weather skeleton |
| Bane of Arthropods | Trial Chamber normal | ender mite mousse |
| Density | Trial Chamber normal | Anvil |
| Smite | Trial Chamber normal | Wither Star |
| Breach | Trial Chamber normal | Turtle Helmet |
| Sharpness | Trial Chamber ominous | Ancient Sword from fishing |
| Windburst | Trial Chamber ominous | breeze rod |
| Fortune | Crafted with coal, copper, emerald, gold, iron, lapis, diamond, quartz, redstone blocks | netherite scraps |
| Swift Sneak | Ancient City | Sculk Sensor |
| Mending | Ancient City | ? |
| Thorns | Dungeon | Guardian Gear dropped from normal guardians |
| Knockback | Woodland Mansion | Slime Block |
| Sweeping Edge | Woodland Mansion | Ancient Blade from Archaeology |
| Unbreaking | End City | Totem of Undying |
| Protection + Variants | End Ships | ? |
| Power | Bastions | Piglin Brute Heads |
| Soul Speed | Bastions | Souls |
| Efficiency | Stronghold | TNT |
| Aqua Affinity | Ocean Monument (dropped by guardians) | Sea Lanterns |
| Punch | Jungle Temple | Skeleton Head |
| Infinity | Jungle Temple | Pillager Head |
| Quick Charge | Pillager Outposts | / |
| Multishot | Pillager Outposts | / |
| Piercing | Pillager Outposts | / |
| Respiration | Shipwrecks | / |
| Depth Strider | Ocean Ruins | Prismarine Shard |
| Charm Boost | Trail Ruins | / |
| Soulbound | Villages, Crafted with soul + book | / |
| Silk Touch | Mineshaft | / |
| Feather Falling | Desert Temple | Parrot Feather |
| Fire Aspect | Overworld-side ruined portals | fire charge |
| Flame | Nether-side ruined portals | magma cream |
| Frost Walker | Igloos | Blue Ice |
| Lure | Fishing | / |
| Luck of the sea | Buried Treasure | secret of the sea (1-4 from shipwrecks) |
| Riptide | Dropped by drowneds rarely | / |
| Loyalty | Dropped by drowneds rarely | / |
| Impaling | Dropped by zombie nautili rarely | / |
| Channeling | Dropped by zombie nautili rarely | / |

A sniffer drops a new item, a seed of ancient magic. this must be watered (dropped in water), lavad, slimed, and soul fired. (its fire resistant). at this point, if you eat the item, it will give you poison and turn into a bulb of ancient magic.
Craft the bulb together with a purification rune (from fishing, archaeology or shop), 6 diamonds and a curse-specific item to make a cursebreaker for that curse. all cursebreakers are made from flowers.
cursebreakers can be applied to books and armor with a curse of that type at anvils for 30 levels and no increased item xp cost.

- curse of vanishing; closed eyeblossom to break
- curse of binding; allium to break

other good ideas
- curse of fragility -> cant go above 50% durability; blue orchid / amethyst glass to break
- curse of disorder -> your items randomly swap places sometimes; wildflowers to break
- curse of draining -> items auto-drain durability to a certain point slowly; lily of the valley / bottle o encahnting to break
- curse of conductivity -> increased chance for the wearer to get struck by lightning in rain or very high chance during thunderstorm when held or worn; lightning rod to break

other mid ideas
- curse of sluggishness -> axe + sword + mace + trident + spear attack cooldown increase significantly
- curse of the breeze -> reduces bow + crossbow accuracy (+ trident)
- curse of misfortune -> anti-fortune
- curse of floating -> you get pushed up out of water

### Information

Information on changes to the base game are all documented in a wiki page on the website. Initially almost all the paragraphs are obfuscated, you gotta get a lost page and use it which removes it and unlocks a random info paragraph. You can get lost pages from:
- Loot
- Archaeology
- Fishing
- Buy in shop
