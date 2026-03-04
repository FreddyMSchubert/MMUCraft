# KubeCraft

> _Toto, I've a feeling we're not in Kansas anymore._

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

Day 1 the server border is 3000x3000

Every day on the server forever, its scheduled, the border increases by 300 blocks

- Half of semester 1: unlock nether

- First week of semester 2: unlock end

## Cosmetics Ideas

hold back armor trims, sell them through shop instead, stop people from duplicating them

we could also make a blueprint item that you need to find in the world which will unlock the cosmetic in the shop

we could also do sale vouchers where you have a 20% discount on the item you buy while you have it in the inventory or something

- Pair of boots that shrink you and your jump
- Pair of boots that expand you and your jump
- Particle effects around player
- Particle trail following player
- Ability to put any item or block you want onto your head
- Item that when held in offhand increases reach
- Max health +x
- youre always walking as if on ice
- night vision hat
- umbrella - slow falling when held
- everlasting beef / eternal steak
- snorkle - more water breathing
- villager hat - reduced trade cost
- more looting armor
- more mount speed hat
- anglers hat - more luck of the sea
- 4 leaf clover - increased fortune when held
- longer invincibility frames
- get speed effect after damage
- chance to light attackers on fire
- double jump / triple jump
- huge jump height
- antidote vessel - decreases harmful effect duration
- quiver - gives you infinity
- swim in air while item held
- ender chest / crafting table opening charm
- spring-loaded shoes - set your jump height while sprinting above 1, allowing you to run up blocks. better than autojump cause autojump slows you down.

- zeus bolt - cooldown 2 hours - summons lightning at target pos
- poseidons trident - when thrown, drags people underwater super quickly
- hades grace - keep your items after death

For more inspiration:
- https://minecraft.wiki/w/Attribute#Armor_toughness
- https://minecraft.wiki/w/Enchanting
- https://minecraft.wiki/w/Effect

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

## Progression Changes

### Enchanting

villagers dont drop enchanted books anymore. instead, get them from structures at increased reliable chances.
you can put enchanted books on lecterns, and the corresponding librarian will offer that trade at high price but reset anvil cost, but will add a random curse to it. This can be removed with the cursebreaker tome.

- looting -> Nether Fortress
- sharpness -> Trial Chamber
- fortune -> Crafted with coal block, copper block, emerald block, gold block, iron block, lapis block, diamond block, quartz block, redstone block
- mending -> ancient city
- protection -> end city
- unbreaking -> woodland mansion
- power -> bastions
- efficiency -> stronghold
- aqua affinity -> ocean monument (dropped by elder guardians)
- respiration -> shipwrecks
- depth strider -> ocean ruins
- charm power (custom, more charms per armor) -> trail ruins
- silk touch -> jungle temple
- feather falling -> desert temple

There are tomes (inspired by quarks mod), which can increase some enchantments even further than normal vanilla max.
A sniffer drops a new item, a bulb of ancient magic. Craft it together with an enchanted book of the right type and the right mob drop to make the tome and 6 diamonds.
tomes can be applied at anvils for 30 levels and no increased item xp cost.

- cursebreaker -> purification rune from archeology or fishing or chests or sometimes traded from max level librarians
- feather falling -> parrot feather
- thorns -> guardian gear dropped from normal guardians
- sharpness -> ancient sword from fishing
- smite -> wither star
- bane of arthropods -> ender mite mousse
- Knockback -> breeze rod
- Fire Aspect -> fire charge
- Looting -> Wither star
- sweeping edge -> ancient blade from archaeology
- efficiency -> silverfish shell dropped from silverfish
- unbreaking -> totem of undying
- fortune -> netherite ingot
- power -> piglin head
- punch -> skeleton head
- luck of the sea -> heart of the sea
- lure -> prismarine shard

### Information

Information on changes to the base game are all documented in a wiki page on the website. Initially almost all the paragraphs are obfuscated, you gotta get a lost page and use it which removes it and unlocks a random info paragraph. You can get lost pages from:
- Loot
- Archaeology
- Fishing
- Buy in shop

### Unlocking the end

Crafting eyes of ender works with a different recipe now.

To craft 16, make this shapeless recipe:

blaze powder
wind charge
resin brick
netherite ingot
a sniffer drop
skulk shard
totem of undying
wither star
ender pearl
