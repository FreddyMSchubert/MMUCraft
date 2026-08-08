# KubeCraft

## Development

Install Docker Compose, Git, GNU Make, Python 3.10 or later, JDK 25, and Node.js.

```sh
git submodule update --init --recursive --depth 1
cp services/api/.env.example services/api/.env
make
```

The API uses port `8080`. The website uses port `3000`. The API and website watch source files. Minecraft does not watch files.

| Command | Result |
| --- | --- |
| `make` | Build and start all services. Follow all service logs. |
| `make restart` | Restart all running services. |
| `make stop` | Stop all services. |
| `make logs SERVICE=mc` | Print all retained Minecraft logs, then follow new logs. Use `api` or `web` for the other services. |
| `make shell SERVICE=mc` | Open a Minecraft container shell. Use `api` or `web` for the other services. |
| `make console` | Attach to the Minecraft server console. |

`make` invokes item staging, protobuf generation, datagen, the mod build, and the resource-pack build. It then builds all images and starts all services. Compose prefixes each log line with the service name. The build tools can reuse outputs that are up to date. The Makefile does not omit a generation task. There is no automatic Minecraft build watcher.

Runtime files are in `.dev/`. API data is in `.dev/api/`. Minecraft data and log files are in `.dev/minecraft/`. Git ignores this directory.

Use `docker compose logs --since 30m api` to read earlier container logs. Use Ctrl+P, Ctrl+Q to detach from the Minecraft console.

Use `make db-generate`, `make db-check`, and `make db-studio` for database work. Commit generated migration files from `services/api/drizzle/`.

## The goal

One main, largely vanilla survival server. Main server..

Every saturday between 4pm to 12pm GMT+0, automatically swapped out for a surprising saturday server, players get redirected. Surprising saturday server runs custom mods that mix things up.
At midnight, everybody gets redirected back to main server, the winner gets announced in chat, and the winner receives a unique custom cosmetic (particle trail, new ability like wearing any item as hat, ...) via custom server-side mod.

By typing a command in chat, you can challenge other players to a minigame, such as dueling, minecraft gartic phone or master builders.

Making custom minecraft content isn't that hard with a fabric mod, so by adding more minigames and a new one-time never-again surprising saturday every week, time required stays relatively low while hopefully keeping interest for longer through a built-in novelty mechanism.

## How

Docker Compose runs the website, API, and optional Minecraft server. The API and Minecraft mod use gRPC. The website sends requests to the API and serves the resource pack. Production uses the same images on one VPS.

## To do list / priority order

- [ ] Get a basic docker image for vanilla server set up
- [ ] Publish and restart the server image on the production host
- [ ] Velocity container that forwards to the main server
- [ ] Server auth using MMU email verification and Minecraft join-code verification.
- [ ] Setup rabbitmq that sends data between scorekeeper and main server mod. every day at 9, scorekeeper initiates border expansion.
- [ ] On website, add shop tab. Create a few placeholder cosmetics. When bought, send rabbitmq message to mod to unlock that cosmetic for player / give player the item.

## Surprising Saturday Ideas

All very unordered, potentially bad, some probably too hard / unfun, whatever - idea collection.

better

- killing a mob turns you into it, kill all mobs (-> https://www.spigotmc.org/resources/morph.8846/)
- stack of blocks in alphabetical order - add something for X + U + V, also kelp block, enable mob drop heads for z Zombie, Budding Amethyst renamed to xeno-amethyst or smth
- lucky block flat world - some lucky blocks give you a hint towards something mega obscure you have to do to win, like suffocating while standing in a cauldron. small world border to annoy each other.
- most unique death messages (same message a second time doesnt count) - keepinv, instant respawn, respawn VERY CLOSE to death location
- vegan run. eating meat, throwing eggs, killing any mob or player kills you immediately from moral decay. Kill dragon via accidental death by unfortunate bed placement. Increase tick speed slightly, make leaves drop more apples, make nether chests have more blaze rods.
- you can only break oak logs, stone, coal ore, iron ore, diamond ore, end stone, gravel, cactus; you can only place crafting table, furnace, obsidian, torch, oxidised copper stairs, TNT, oak boats, green beds. defeat dragon
- bloodlust challenge. you cant eat, killing things satisfyes your hunger. killing players is an enchanted golden apple, played with small world border. longest time between deaths wins.
- randomised block / mob / chest loot tables, goal: die to the void
- who can get the most of item x in their ender chest (e.g. watermelons). griefing and theft is encouraged and recommended.
- https://modrinth.com/datapack/chunklock, whoever gets the most gold wins.
- one block (per person that joins)

unserted / potentially worse

- achievements who can get the most
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
- collect every type of flower
- perma-night
- a totally normal vanilla world (herobrine) -> https://modrinth.com/datapack/from-the-fog https://www.curseforge.com/minecraft/mc-mods/sever-side-horror https://www.curseforge.com/minecraft/texture-packs/true-darkness
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
- hide and seek. special retextured blocks that give a point when broken hidden around map. every 10 minutes you get your relative direction to the nearest one
- hide and seek - start a game with command, players get a compass pointing to you when outside a certain distance to you. if they dont find you within a certain time you get a point, otherwise winner gets a point. need some way to regulate people going underground.
- most unique effects applied at once
- most unique mobs killed (kill each mob once)
- most wardens killed
- most sniffers bread
- speedrun, quickest x e.g. ender dragon death, wind charged 3 book
- one hit wither with mace
- longest online time between deaths, everyone can see each others positions, small world border
- randomised crafting recipes
- everyone shares a synced inventory trying to kill the dragon
- every like 2 minutes your location is swapped with another player's
- monster hunter alphabet challenge - kill mobs in alphabetical order
- fetchr / bingo full or only one line - maybe as gamemode

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
- quicker happy ghast speed charm
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
- respawn closer to death location after death
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
- kitty ears purrrrrrrr https://mccisland.fandom.com/wiki/Cat_Ears
- duck hat
- cute new chicken
- bow ribbon
- Egg Yolk https://mccisland.fandom.com/wiki/Egg_Yolk
- Disguise https://mccisland.fandom.com/wiki/Disguise
- Cardboard https://mccisland.fandom.com/wiki/Cardboard_Box

- witch nose (with dyeable pimple)
- pile of coins
- witch hat from vanilla
- different hair types
- rainbow clown hair
- banana peel
- antenna
- light bulb
- this meme, sad and happy version -> https://media.tenor.com/cI1t4CvrgogAAAAe/crying-under-mask-feels.png
- alien head
- top hat
- viking helmet
- animals (sitting on hat, or animal shaped hat)
- steampunk hat
- party cone hat
- santa hat
- lever
- dragon skull head
- armor / knights
- different glasses / shades
- minion eyes
- mario / luigi hat
- night cap
- bull horns
- mario-toad-like mushroom cap
- fnaf stuff, springtrap mask
- masks from majoras mask
- turtle shell on head
- antlers
- lying down cat / dog / fox / frog / bee
- lil octopusses
- ghast hat so it looks your face is the ghasts face. fully engulfs your head but hole in front
- flower on head
- flower crown
- gas mask
- Shark https://mccisland.fandom.com/wiki/Shark
- Sensei Hat https://mccisland.fandom.com/wiki/Sensei
- Safari Hat https://mccisland.fandom.com/wiki/Safari_Hat
- Sleeping Allay https://sketchfab.com/3d-models/sleep-buddies-46e9c23a61ee4bbbb571653f59323394
- Sprout
- Banana Head https://mccisland.fandom.com/wiki/Banana_Head

- Gravestone
- Kettle

## Fishing Changes

More Fish, more junk, more treasure
Different Fish Loottables for Warm, Cold, Temperate, End
bunch of other variances based on moon cycle, height in world
sometimes an enemy jumps out at you and attacks you
animal crossing fishing minigame

relevant items
- lucky charm -> luck increase
- potion of luck -> luck increase
- 1, 2, 3, 4 leaf clover -> craft into 4 leaf clovers used for luck potions

- higher chance of fish -> worms
- higher chance of item -> item magnet
- higher chance for less fish bounces, tiered -> barbed hook
- basically lure -> flybait
- relaxed fish catching timing window -> fish sleeping pill

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

fish - retreat stay in struggle

smol fish - 1,3 0,9 0,5 2
big fih - 1 0,25 0,3 2,6
smol fih 2 - 1 0,2 0,5 2,1

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

Enchanting tables use the vanilla rules, enchantments, and levels.
Vanilla enchanted-book loot remains available. Some structures and mobs have an additional chance to give specific enchanted books.
Librarians keep their vanilla random enchanted-book trades. Soulbound is not in the vanilla trade pool. Put an enchanted book on a librarian's lectern to add a trade that duplicates that book.
Anvils keep the custom repair and combination rules. Item repairs keep the enchantments from the first item. Material repairs support netherite items with diamonds and do not increase the XP cost. Book combinations add the levels together. For example, Sharpness I plus Sharpness II gives Sharpness III.

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
| Mending | Ancient City | Creeper Head |
| Thorns | Dungeon | Guardian Gear dropped from normal guardians |
| Knockback | Woodland Mansion | Slime Block |
| Sweeping Edge | Woodland Mansion | Ancient Blade from Archaeology |
| Unbreaking | End City | Totem of Undying |
| Protection + Variants | End Ships | Green Candles |
| Power | Bastions | Piglin Brute Heads |
| Soul Speed | Bastions | Souls |
| Efficiency | Stronghold | TNT |
| Aqua Affinity | Ocean Monument (dropped by guardians) | Sea Lanterns |
| Punch | Jungle Temple | Skeleton Head |
| Infinity | Jungle Temple | Pillager Head |
| Quick Charge | Pillager Outposts | Fireworks |
| Multishot | Pillager Outposts | Quiver |
| Piercing | Pillager Outposts | Ocelot Claw |
| Respiration | Shipwrecks | Turtle Helmet |
| Depth Strider | Ocean Ruins | Prismarine Shard |
| Charm Boost | Trail Ruins | Raw Gold Block |
| Soulbound | Crafted with a soul and a book | Soul |
| Silk Touch | Mineshaft | brain_coral_block |
| Feather Falling | Desert Temple | Parrot Feather |
| Fire Aspect | Overworld-side ruined portals | fire charge |
| Flame | Nether-side ruined portals | magma cream |
| Frost Walker | Igloos | Blue Ice |
| Lure | Fishing | Some sort of rare-ish fish |
| Luck of the sea | Buried Treasure | secret of the sea (1-4 from shipwrecks) |
| Riptide | Dropped by drowneds rarely | Phantom Membrane |
| Loyalty | Dropped by drowneds rarely | Dog Collar by killing a tamed dog |
| Impaling | Dropped by zombie nautili rarely | Heart of the Sea |
| Channeling | Dropped by zombie nautili rarely | Waxed Oxidized Lightning Rod |

amethyst

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
- curse of sickness -> fills your inventory with unstackable slime balls over time. just annoying.

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

## Rewards Ideas

- Submit an mp3, and then putting your head on a jokebox plays that sound
- Bedrock Breaker Pickaxe
- Amethyst Cluster Pickaxe
- Submit image to put on hat
- Convo with oderzo about new hat to add
- Ability to put anything in any armor slot
- Make chestplate an elytra
- custom dog skin of choice - should work for all mobs with variants i believe. so cats as well.

## Recommended mods list

Not necessary but helpful

- https://modrinth.com/mod/advancements-reloaded makes the advancements screen much more bearable
- https://modrinth.com/mod/chat-heads for better chatting
- https://modrinth.com/mod/resourcepackcached https://modrinth.com/mod/keep-the-resourcepack one of these two if either of them updates. makes joining less annoying
- https://modrinth.com/mod/appleskin this so you can easily see all the changed food values
- https://modrinth.com/mod/fancy-toasts this if they update cause advancements are important and these are cool and fancy

theres even a modrinth server thing we could set up so you dont have to install everything yourself might be cool if its possible

## DAily quests ideas

- Eat x of item y
- Kill x of mob y
- Breed x mob
- Trade for x emeralds
- Trade for x y (random pick from villager trades)
- Brew a potion of x
- Die to mob x
- Step foot in structure x
- Throw a splash potion at another player
- Brush suspicious sand / gravel x times
- Kill a player
- Kill player x (random player currently online, must update when they leave)
- Fish x times
- Catch fish x
- Earn x experience
- Break x block
- Trade with villager profession x
- Ride x for a few meters
- Take x damage
- Deal x damage
- Craft x item
- Shear x sheep

- Ignite a creeper
- REflect ghast fireball
- Use enderpearl
- Barter with piglins
- Ride a pig
- Jump on a slime block
- Defeat a raid
- Defeat the ender dragon
- Defeat the wither
- Use a wind charge
- Use a flint and steel / fire charge
- Be set on fire
- Light a TNT
- rename an item
- light a candle
- waterlog a thing
- use a potion of displacement
- ride a minecart underwater
- milk a cow
- potion of summoning
- mine a spawner
- brush an armadillo
- use a mace to kill something
- throw snowballs / eggs at someone
- use a totem of undying
- look at a mob through a spyglass
- die
- play a goat horn
- plant x crops
- play a music disk
- wax a copper thing
- dewax a copper thing
- revive a copper golem
- make golem (copper iron snow)
- ring a bell
- stand on a dripleaf
- get squished by an anvil
- get struck by lightning
- put a flower in a pot
- hang a painting
- put a book in a chiseled bookshelf
- read a joke book
- break infested stone
- kick a sulfur cube
- mine a budding amethyst
- mine reinforced deepslate
- receive x effect
- get prickeld by a cactus or sweet berry bush
- die for death reason x
- trigger a sculk sensor three times, spawning a warden
- play a note block with instrument x
- turn concrete powder to concrete
- apply x customizations to a banner
- right click the fletching table, hoping it does something each time, yet it never does, fix bedrock mojang, x times
- fall 100 blocks down
- mine bedrock for 3 minutes (youll get there, keep holding!)
- make eye contact with an enderman
- anger a piglin
- stare at a creaking and don't blink. Don't even blink. Blink and you're dead. They are fast, faster than you could believe. Don't turn your back, don't turn away, and DON'T BLINK.
- make an item frame invisible / glowing
- trade with a wandering trader
- 
