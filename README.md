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
- [nginx](https://nginx.org/) for a tiny web server that enables minigame world downloads
- **ScoreKeeper** - Some centralized custom program that will
  - handles scoring while surprising saturday is running
  - maintain inter-server player stats (will be called by main pod mod for permission checks)
  - delete nginx-served world backups after some time
  - maybe even auto-update the server resource pack
  - anything else we need
- This repo will host the dynamic enforced server side resource pack, allowing for cosmetics to be rewarded to surprising saturday winners

Setup will not be elastic and everything will be on one big VPS cause we don't have the budget for more.

The surprising saturday & minigame world custom mods will be developed in this repo & then the surprising saturday docker image will be updated each week.

## Surprising Saturday Ideas

All very unordered, potentially bad, some probably too hard / unfun, whatever - idea collection.

- achievements who can get the most
- start in nether
- first death to void wins
- start in end islands
- upside down end gen
- sky islands world gen
- cave world world gen
- superflat world gen
- amplified world gen
- you can only breathe when touching water or snow (even in cauldron), defeat ender dragon
- the purge. played on copy of survival server, but anything done is reset at midnight. kill the most to win
- lifesteal
- hardcore
- every half an hour another players pos is revealed in chat. person who kills them gets a point. most points wins
- who can get the most of item x in their nether chest (e.g. watermelons)
- hide and seek. special retextured blocks that give a point when broken hidden around map. every 10 minutes you get your relative direction to the nearest one
- hide and seek - start a game with command, players get a compass pointing to you when outside a certain distance to you. if they dont find you within a certain time you get a point, otherwise winner gets a point. need some way to regulate people going underground.
- most unique effects applied at once
- most unique mobs killed (kill each mob once)
- most wardens killed
- most unique death messages (same message a second time doesnt count)
- most sniffers bread
- speedrun, quickest x e.g. ender dragon death, wind charged 3 book
- one hit wither with mace
- longest time between deaths
- randomised block / mob loot tables
- randomised crafting recipes
- everyone shares a synced inventory trying to kill the dragon
- every like 2 minutes your location is swapped with another player's

With some of these multiple players could get them at the same time, so we should in the ScoreKeeper just take record whenever theres a clear winner in the current result state, even if another player also wins a millisecond later, thus making winning together essentially randomize the winner.

Might be nice to make some challenges have multiple winners to encourage collaboration.
