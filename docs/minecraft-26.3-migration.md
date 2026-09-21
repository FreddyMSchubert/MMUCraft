# Minecraft 26.3 migration report

Date: 20 September 2026.

The repository now targets Minecraft Java 26.3. The mod, proxy plugin, data, and resource pack build successfully. An isolated backend started with the production mod set. The final deployable JAR also started through the official Fabric launcher with that mod set. An isolated Velocity proxy loaded both plugins and reported that backend as healthy. These results support a staging release. They do not verify an upgrade of the live world or a player session.

No production service was changed. No real world, database, or player inventory was used. The runtime checks used temporary directories and local ports. The temporary servers were stopped after the checks. Docker containers were not started.

## 1. Versions and dependencies

| Component | Target | Decision |
| --- | --- | --- |
| Minecraft | 26.3; protocol 777 | Use in the image, both Compose files, mod build, website, and asset URLs. |
| Java | 25 | Keep the existing required major version. |
| Fabric Loader | 0.19.5 | Use the same version in the build and server configurations. |
| Fabric API | 0.161.0+26.3 | Pin the build and runtime dependency. |
| Fabric Loom | 1.17.21 | Update the Minecraft build plugin. |
| Gradle | 9.6.0 | Update the wrapper. |
| Velocity | 4.1.2-SNAPSHOT, build 29 | Pin the build that supports 26.3. |
| Velocity API | 4.1.2-SNAPSHOT | Compile the custom proxy plugin against the matching API. |
| FabricProxyLite | 2.12.0 | Keep the compatible forwarding dependency. |
| Simple Voice Chat, Fabric | 2.6.24+26.3; `OLnMVWXy` | Update the backend dependency. This release is marked beta. |
| Simple Voice Chat, Velocity | 2.6.18; `ES87t4lm` | Keep the compatible proxy plugin. |
| Ledger | 1.3.24; `f72cP4Ni` | Keep audit logging with a verified compatible release. |
| Fabric Language Kotlin | 1.14.1+kotlin.2.4.20; `eRRZzGMc` | Pin the runtime required by Ledger. |
| Lithium | 0.26.1+mc26.3; `WXHRsMRl` | Keep as optional. |
| FerriteCore | 9.0.0-fabric; `d5ddUdiB` | Keep as optional. |
| C2ME | 0.4.2-alpha.0.85+26.3; `XgxQ7H1c` | Keep as optional. |
| ScalableLux | 0.3.0-alpha.0.6+26.3; `g4eqNSKd` | Keep as optional. |
| ServerCore | 1.5.20+26.3; `LCG1Bm84` | Keep as optional. |
| ZFastNoise | 1.1.0-beta.5+26.3; `bjeF0iDC` | Keep as optional. Its required ZConfig dependency resolved in the runtime check. |
| Krypton | Removed from the production download list | No compatible 26.3 release was available during this work. |

The optional dependency syntax is `project?:version-id`. The question mark lets the image skip a missing compatible download. It does not make a loaded mod safe if that mod fails during startup. Remove an optional mod from the configuration if staging finds a runtime failure. Core forwarding, API, voice, and audit dependencies remain mandatory.

The mod metadata now requires `~26.3`. The old open-ended minimum could let a future incompatible Minecraft release load the mod.

The existing Java 25 images, network boundaries, authentication rules, database schema, and deployment sequence remain in place. Dependencies unrelated to this Minecraft migration were not updated without a compatibility reason.

Sources: [Minecraft release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3), [Fabric migration notes](https://fabricmc.net/2026/09/15/263.html), [Velocity build metadata](https://fill.papermc.io/v3/projects/velocity/versions/4.1.2-SNAPSHOT/builds/29), and [Modrinth image configuration](https://docker-minecraft-server.readthedocs.io/en/latest/mods-and-plugins/modrinth/). Mod versions were checked against publisher metadata and loaded together in the temporary backend.

## 2. Problems found and changes made

### Java and mixin compatibility

1. **Player item drops now require a prediction argument.** Updated command rewards, coins, wallet contents, backpack returns, crafting staff output, unlock books, daily rewards, shop delivery, maps, mini blocks, and recovered armour contents to use `Prediction.SERVER_ONLY`. These are server actions. The server must create each dropped entity once.
2. **Death drops need different semantics from ordinary player drops.** Updated `NoSoulboundItemDropping` through `createItemStackToDrop`, followed by entity insertion. Kept random death scatter, the existing extended lifetime, and inventory removal. This avoids adding ordinary drop statistics or changing soulbound behaviour during the API port.
3. **The item break callback now receives an `ItemStack`.** Updated armour-content recovery and fishing rod break notifications. The old `Consumer<Item>` signature was incompatible.
4. **Axe and hoe classes were removed.** Saw Belt and Farming Boots now use the native axe and hoe item tags. The tool restrictions remain in place. Poplar tree handling uses the existing block tags.
5. **Movement and display fields changed.** Updated Winged Shoes and Spider Pajamas from `hurtMarked` to `syncVelocity`. Updated fish display invulnerability to `setPermanentlyInvulnerable`.
6. **Loot and bundle methods changed.** Updated loot context reads to `getOptional`. Updated backpack bundle inspection to `itemCopies`. Kept the existing nested-storage checks and loot rules.
7. **Component patch iteration changed.** `StackComponentPatchUtil` now checks the native added and removed component sets. It uses the native patch application method. A removed component must be absent; an added component must have the specified value. Removed raw casts and duplicate application code.
8. **Fake item identity must remain protected.** `FakeStackDef` rejects both addition and removal of `custom_model_data` in a supplied patch. This prevents a stack description from changing a fake item's identity.
9. **Advancement display data and packet layout changed.** Updated record accessors, generated display backgrounds, and advancement identifier arguments. The money-tooltip mixin now processes `PositionedAdvancement` entries and preserves each entry's coordinates. Rewards, criteria, and visibility remain unchanged.
10. **Three other mixin targets changed.** Updated the Enderman class name, the projectile-deflection hit-position argument, and the block-break exhaustion receiver to `ServerPlayer`. A compile alone cannot prove that these injections work. The runtime check forced all configured mixin target classes to load.
11. **Potion advancement events now supply `PotionContents`.** The daily-task hook extracts the optional registered potion. A potion without a registered base does not cause a null failure.
12. **Vanilla registry bootstrap changed.** Updated the particle, glider, and daily catalogue checks to use `createWorldLookup`.

### Luck brewing

13. **The old brewing implementation was removed upstream.** Deleted the obsolete `PotionBrewingMixin`. Added a small `LuckBrewingRecipe` subclass and registered its serializer through the existing recipe infrastructure. Added one data recipe for each normal, splash, and lingering container.
14. **The reagent's vanilla item is not sufficient identification.** The recipe first checks the native input and reagent predicates. It then checks the fake item ID `4-leaf-clover`. A normal Heart of the Sea or another clover cannot replace it.
15. **A documented input field did not match the actual codec.** The initial input predicate used singular `potion`. The codec ignored it, so water could match. A negative check exposed this. The input now uses `potion_contents.potions`; the output component correctly retains singular `potion`. The check rejects water, an ordinary Heart of the Sea, and a three-leaf clover. It verifies Luck output and the original container type for all three recipes.

The new `checkMigration` task runs from Gradle `check`. It covers brewing and component patch semantics in one small executable check. It adds no test framework.

### Hopper filters and new content

16. **Existing item-family lists did not contain new items.** Updated these 32 groups: `beds`, `boats`, `buttons`, `chest_boats`, `concrete_all`, `doors`, `fence_gates`, `fences`, `hanging_signs`, `leaves`, `logs`, `mushrooms_and_fungi`, `planks`, `pressure_plates`, `saplings`, `shelves`, `signs`, `slabs`, `stairs`, `stripped_logs`, `stripped_wood`, `trapdoors`, `wood`, `wood_items`, `wooden_buttons`, `wooden_doors`, `wooden_fences`, `wooden_pressure_plates`, `wooden_slabs`, `wooden_stairs`, `wooden_trapdoors`, and `wool_all`.
17. **New filter choices were needed.** Added `wool_stairs`, `wool_slabs`, `concrete_stairs`, `concrete_slabs`, `cushions`, `woodset_poplar`, `maps`, and `explorer_maps`. The coloured groups contain all 16 colours. The Poplar set contains 22 items. The map groups include the distinct explorer-map item types.
18. **Broad group names needed to match their contents.** Updated the wool and concrete group display names. Existing group IDs remain unchanged so saved filters can still resolve them. Added Straw Bed to beds and Shelf Mushroom to mushrooms and fungi.
19. **Heater and mushroom tags needed new members.** Added Poplar log, wood, and their stripped variants to the existing level-two heater tag. Added Shelf Mushroom to the mushroom tag. Added stripped Poplar log to the static stripped-log item tag.
20. **New leaf particles fit the existing trail system.** Added red, orange, and yellow Poplar leaf trails, each with its matching leaf ingredient. Updated the knowledge page. Existing string IDs, membership restrictions, recipes, and serialization rules continue to apply. The existing trail check iterates the new entries too.

All 3,085 explicit vanilla item references in the hopper catalogue were checked against the 26.3 client item assets. None were unknown. Data validation accepts 371 custom items and 280 filter groups.

### Data loading and world generation

21. **Configured features moved to the new feature format.** Moved `alien_debris` from `worldgen/configured_feature` to `worldgen/feature`, removed the obsolete wrapper, and converted its target block state to the new string form. Kept the placement and intended block.
22. **An existing recipe filename contained uppercase letters.** Renamed `vinyl-player-9AM.json` to `vinyl-player-9am.json`. Minecraft identifiers require lowercase paths. The old recipe could be ignored. Recorded the case-only rename in Git so Linux receives the fix.
23. **The built-in advancement-filter pack had old metadata.** Updated its supported data format to 121.0.

The scan also covered climate-based fishing, claim interactions, loot-table selection, map tools, and structure placement. Fishing derives its conditions from the biome and world, so it does not need a new fixed Dappled Forest entry. Claim interaction hooks check generic entities, including cushions. Chest loot hooks use the `chests/` prefix. Small-map tools continue to accept normal maps only; they do not convert the new explorer maps into map art. These paths still need the staging checks below.

### Resource pack and website

24. **Resource format changed.** Updated the generator, general pack, and shield pack to minimum and maximum format 97.1.
25. **Element shading fields changed.** Replaced ten `shade: false` fields with `shade_direction_override: "up"` across the Googly Eyes, Deer Antlers, Warden Antlers, and Fire Hat models. Updated the generator documentation.
26. **The merger could retain an upstream pack's metadata.** After merging, the build now copies the generated `pack.mcmeta` into the result. This makes the final description and format bounds match the project. Updating only the input pack metadata did not guarantee that result.
27. **An upstream potion model was malformed.** Almost Vanilla Potions supplied an Oozing splash-potion model with an extra closing brace. Added a corrected local override at the same resource path. Kept the source pack pinned and preserved its model content.
28. **External resource packs do not yet advertise 26.3 support.** Kept the existing EvenBetterEnchants, Almost Vanilla Potions, and Fancy Beds pins. Inspected the merged files for removed fields and shader dependencies. All 1,099 JSON and metadata files parse. Client rendering remains a release check.
29. **Current-version text and asset URLs were stale.** Updated the website shell, join/authentication panel, money knowledge link, README, and API and website vanilla asset bases to 26.3.
30. **One old version value was accurate historical data.** Renamed the old panorama directory and selector to `chaos-cubed`. Kept `sourceVersion: "26.2"` in its source manifest because those six images are from that release. Changing that provenance to 26.3 would be false. The existing panorama sequence remains in place.
31. **Generated output needed to stay in sync.** Rebuilt the resource archive, its website copy, and generated server properties. The two ZIP files are identical and the generated SHA-1 agrees. The checked archive SHA-1 is `3f199c674212f68cacdf9e4a1b12e798b9b7918a`. A later rebuild can produce a different archive hash; compare the outputs from the same build.

### Verification problems and environment limits

32. **Slime Detector checks were stale.** The checks still expected 10–40 ticks and 16-pixel static images. The existing implementation uses 8–42 ticks; the existing assets use 18-pixel frames. Updated the assertions for the current timing, static frames, and four/eight-frame animations. Did not change gameplay or textures.
33. **The shell used Node 26, but the project requires Node 24.** Downloaded and verified Node 24.14.0 in a temporary directory. Re-ran formatting, lint, type checks, and generator tests with that runtime. All passed.
34. **The default Python environment lacked `jsonschema`.** Used a temporary virtual environment with the project's existing validation dependency. The catalogue and staging checks passed.
35. **The first direct Velocity download returned HTTP 403.** Downloaded from the same official object URL with an identifying user agent. Verified the published SHA-256 before execution: `01a960340f0dc5d5af99e848513e395c5d406519359b0a5e2c66f539bae03dcc`.
36. **The first Loom smoke launch did not use the intended working directory.** It stopped at the EULA check. Configured Loom's server `runDir` explicitly and used a temporary world for subsequent runs.
37. **The local API was intentionally absent.** Backend claim, daily-task, and toggle retries were expected in that test. A separate local HTTP stub verified the proxy's bearer authentication, server registration, and live backend health exchange. This did not test real login decisions or gRPC round trips.
38. **Native and development warnings remain.** C2ME reported that native acceleration was unavailable on macOS ARM64 and used its fallback. Voice Chat reported the development launch mode. Gradle and Java libraries emitted deprecation warnings. None stopped these checks. Linux container behaviour and sustained load still need staging verification.
39. **The first temporary world lacked the minecart experiment.** The mod warned when it tried to set the experiment's gamerule. The final packaged-JAR check enabled the experiment and did not produce that warning. The production template enables the experiment for new worlds. Check that the existing production world still has the intended experiment and speed after conversion.

40. **The standalone smoke setup initially omitted Fabric API.** Loom had supplied it on the development classpath. Added the exact pinned Fabric API JAR to the temporary standalone mod directory and reran the test. Both Compose configurations already install it. The packaged mod then started with the full set, opened its gRPC and voice services, and passed the forced mixin audit.

## 3. Verification completed

| Check | Result and scope |
| --- | --- |
| Mod build and datagen | Passed on Java 25 with the updated wrapper and dependencies. |
| Migration check | Passed positive and negative brewing cases and component patch cases. |
| Existing Java checks | Particle trails, player colours, glider, Slime Detector, fishing, sparse structures, and daily catalogue passed. |
| Velocity plugin build | Passed against the updated API. |
| Backend runtime | MainMod started alone and with the production dependencies. The final deployable JAR also passed through the official Fabric launcher. All configured mixin targets passed the forced audit. |
| Data reload and save | Passed on the temporary world with the production mod set. |
| Protocol status | Backend and Velocity both returned protocol 777. |
| Proxy runtime | Custom plugin and Voice Chat loaded. Voice Chat bound its temporary UDP port. |
| Proxy control exchange | Temporary authenticated API response registered the backend. The next sync reported it online. |
| Data schema and staging | 371 items and 280 groups passed. Staged files match source data. |
| Hopper item IDs | All 3,085 explicit vanilla references exist in the target client assets. |
| Resource pack | Built; 1,099 JSON/metadata files parsed; metadata is 97.1; website copy and generated server hash match. |
| Website/API/generator checks | `npm run check` passed on Node 24.14.0. |
| Generator tests | All five existing tests passed on Node 24.14.0. |
| Deployment checks | All 17 existing scenarios passed, including backup-free development. |
| Diff whitespace | `git diff --check` passed. |

Useful repeatable commands from the repository root, with Java 25, Node 24, and the existing Python validation dependencies installed:

```sh
python3 minecraft/main/data/validation/validate.py --root minecraft/main
python3 minecraft/main/stage_item_data.py --root minecraft/main
./minecraft/main/mod/gradlew -p minecraft/main/mod build checkParticleTrails checkGlider checkSlimeDetector checkDailyCatalog runDatagen
./minecraft/main/mod/gradlew -p services/velocity/plugin build
npm run check
npm test --prefix minecraft/main/respack/items-respack-generator
python3 deploy/check-deployment.py
python3 minecraft/main/respack/build-main-pack.py
git diff --check
```

The root `npm test` starts production-container Playwright tests. It was not run because this task excluded Docker startup. The temporary mixin audit and protocol/API probes were execution checks; they are not added to the production mod.

## 4. Staging checklist

Use a copy of the production world, player files, API database, configuration, and world-installed datapacks. Keep the original backup separate. A new empty world cannot prove that old saved data converts correctly. Do not try to roll back by opening an upgraded world with the old server; restore the matching world and database backups instead.

### Critical infrastructure

- [ ] Build the images through the normal deployment path. Confirm the logged Minecraft, Loader, API, proxy, and mod versions match the table.
- [ ] Inspect the resulting mod directory. Confirm old incompatible JARs, including any old Krypton JAR, are removed. Check the image's managed-download cleanup on the existing volume.
- [ ] Start a copied world. Confirm no missing registry, recipe, tag, mixin, or datapack errors. Include datapacks stored only in that world, such as any installed advancement packs.
- [ ] Join with an allowed account using a 26.3 client. Confirm the correct UUID, skin, inventory, location, membership, and assigned backend.
- [ ] Try an unlinked, unapproved, restricted, and banned account. Each must receive the intended denial.
- [ ] Stop API access temporarily. New login must fail closed. Restore it and confirm recovery.
- [ ] Verify direct public access to the backend and gRPC ports is blocked. Keep modern forwarding enabled. A wrong forwarding secret must not permit a backend login.
- [ ] Change the assigned backend, move a player through the admin controls, and exercise maintenance mode. Verify the routing and disconnect messages.
- [ ] Restart the backend while the proxy stays up. Check health reporting, connection failure handling, and recovery.
- [ ] Use the normal deployment flow on staging. Verify player evacuation, shutdown, backup, startup, and maintenance-state removal.
- [ ] Confirm API/mod gRPC operations work in both directions. Claims, dailies, toggles, stats, and shop data must load without persistent retries.
- [ ] Verify Ledger records a placed block, a broken block, and a container change. Exercise lookup and a small rollback on staging.
- [ ] Check metrics, health checks, and dashboards. Compare idle and active tick time, memory, chunk loading, and save duration with the previous baseline.
- [ ] Restart again after saving. Confirm player data, inventories, custom items, claims, and balances persist.

### Voice and required pack

- [ ] Join with the target Voice Chat client mod. Run `/voicechat test <player>` and confirm the connection.
- [ ] With two clients, test normal speech at several distances, whispering, groups, muting, and reconnection. Confirm UDP works through the proxy's public address.
- [ ] Join without the voice client mod. Normal gameplay must still work.
- [ ] Accept the required resource pack. Check download, UUID/hash refresh, and successful client reload. Declining must follow the existing required-pack policy.
- [ ] Compare the served ZIP hash with generated server properties. Test after a fresh download and with a previously cached pack.
- [ ] Inspect all four changed cosmetic models in inventory, in the hand, and when equipped or placed. Check lighting, transparency, and orientation.
- [ ] Inspect normal, splash, lingering, and Oozing potion models. Check enchantment visuals, shields, existing coloured beds, and the new Straw Bed.
- [ ] Inspect representative custom fish, charms, mini blocks, coins, armour, animated Slime Detectors, and decoration blocks. Check the client log for missing textures and invalid model errors.

### Filters and new content

- [ ] Open each of the eight new filter groups. Verify labels, icons, and all expected items.
- [ ] Pass every colour of wool stair, wool slab, concrete stair, concrete slab, and cushion through its specific group. Reject an unrelated item.
- [ ] Test the broad wool, concrete, stair, and slab groups. Confirm the new items pass and the old items still pass.
- [ ] Test Poplar logs, stripped logs, wood, stripped wood, planks, leaves, sapling, boats, and all wood building forms in the appropriate existing groups.
- [ ] Test Shelf Mushroom and Straw Bed in their groups. Test the new explorer-map item types in map groups.
- [ ] Reload a saved filter configured before the upgrade. Check item-specific and group-specific filters, allow/deny modes, hopper direction, and full destination behaviour.
- [ ] Break and replace a configured hopper, unload its chunk, and restart. Confirm its settings persist as designed.
- [ ] Use Saw Belt on a Poplar tree with a supported axe. Verify tool damage, limits, leaves/log handling, and claim boundaries.
- [ ] Use the heater on all four Poplar log/wood forms. Verify the intended conversion and enchantment-level requirement.
- [ ] Use the three Poplar leaf trail ingredients. Test bows and other supported trail containers, membership restrictions, persistence, and visible particle colours.
- [ ] Place, sit on, and break cushions inside and outside a claim. Test as the owner, a trusted player, and an unrelated player.
- [ ] Test Straw Bed use in the Overworld and other dimensions. Confirm the intended vanilla behaviour and claim protection.

### Items, economy, and progress

- [ ] Brew Luck with a four-leaf clover and an Awkward potion in all three containers. Check ingredient consumption, fuel, duration, and output.
- [ ] Try water, a normal Heart of the Sea, and other clovers. None must produce Luck through the custom recipe. Confirm ordinary vanilla brewing still works.
- [ ] Complete a brewing daily. Also test Enderman eye contact and projectile reflection daily events.
- [ ] Claim daily and shop rewards with room in inventory, then with a full inventory. Each reward must appear exactly once. Check cancellation and insufficient funds.
- [ ] Test coin withdrawal, conversion, wallet return, fake-item give, mini-block give, crafting staff output, and unlock-book return with a full inventory. Confirm no item loss or duplication.
- [ ] Test fake stack descriptions with added and removed components. Attempts to add or remove `custom_model_data` must be rejected.
- [ ] Store and recover backpack contents, including permitted bundles. Verify the existing restrictions on nested storage and contents after restart.
- [ ] Die with mixed soulbound and ordinary items, equipped charms, and a full inventory. Confirm retention, dropped contents, scatter, lifetime, and respawn. Repeat with the relevant keep-inventory rules.
- [ ] Break armour that contains charms. Check an empty equipment slot, a full inventory, and multiple recovered charms. Each charm must survive exactly once.
- [ ] Break a fishing rod. Confirm break feedback and no duplicate catch. Test fish-shadow visibility and normal fishing rewards.
- [ ] Use Winged Shoes and Spider Pajamas with another player watching. Check motion synchronization, jump limits, fall damage, and logout/rejoin.
- [ ] Harvest with Farming Boots using each supported hoe. Test an unsupported held item, tool breakage, crop maturity, and a claim boundary.
- [ ] Mine with and without Endurance. Check the hunger effect and ordinary block drops.
- [ ] Open the advancement screen. Check positions, icons, backgrounds, reward tooltips, and member/non-member values.
- [ ] Earn and reload a normal advancement and a mastery advancement. Confirm the reward is paid once and existing progress remains. Check announcements through the normal staging integration.
- [ ] Test unlock books, chest coins, and loot modifiers in existing structures and a new Abandoned Camp. Do not infer a loot failure from one unlucky random roll.
- [ ] Craft the 9AM vinyl player. Confirm its recipe now loads and the resulting decoration and sound work.

### Worlds, maps, and website

- [ ] Travel through old and newly generated chunks in every enabled dimension. Check custom biomes, alien debris, structures, portals, and chunk borders.
- [ ] Find a Dappled Forest and Abandoned Camp in new chunks. Check population, loot, maps, and the existing structure-density rules.
- [ ] Test converted explorer maps from the copied world and newly obtained maps. Check type, destination, display, and cloning.
- [ ] Test ordinary maps, small maps, invisible maps, frames, and map-related overflow drops. Explorer maps must retain their distinct identity.
- [ ] Check minecart experiment status and the intended speed. Test an existing railway after restart.
- [ ] Open the join panel and site footer. Confirm 26.3 instructions and the correct Voice Chat guidance.
- [ ] Open shop, item, and knowledge pages with old items and new vanilla items. Confirm asset URLs, icons, and 3D previews resolve.
- [ ] View the landing page across its panorama transition. Confirm the renamed historical panorama loads and there are no missing image requests.
- [ ] Run the existing Playwright suite through its normal container workflow when staging is available.

## 5. Other bug and remaining limits

**Existing gRPC bind-address bug:** `GrpcBridge.start` reads `MOD_GRPC_HOST` and includes it in the log, but constructs the server with `ServerBuilder.forPort(port)`. That method does not use the requested host. A configured loopback host therefore does not restrict the bind as the log implies. The current container setup relies on its private network and unpublished backend ports. This bug is reported separately; this migration does not change the transport implementation. A follow-up should bind the existing Netty server builder to an explicit socket address and verify the actual listening address.

**External state is outside this checkout:** The runtime checks did not include a production database, real clients, world-installed third-party datapacks, or the real world. The resource-pack inputs do not yet declare 26.3 support upstream. C2ME, ScalableLux, ZFastNoise, and backend Voice Chat include prerelease versions. Keep the staging checks as release requirements. If an optimisation causes a failure, remove it first and keep the mandatory infrastructure intact.

**Existing fixed catalogues stay fixed:** Mini-block heads use authored texture profiles, and daily tasks use a curated catalogue. This migration does not invent texture profiles or automatically add every new vanilla item as a reward. Direct item filters still support new items that do not have a dedicated group.

## 6. Check your understanding

1. Why can a recipe parse successfully and still accept an invalid input? Why was the water-bottle check necessary?
2. Why does a successful Java compile not prove that the advancement or exhaustion mixin works?
3. Why do we preserve `PositionedAdvancement.x/y` and fake-item component identity during this port?
4. Why does `project?:version-id` not guarantee that an optional mod cannot stop the server?
5. Why is a clean-world startup insufficient evidence for a safe production upgrade?
6. Why can a log that says `127.0.0.1` still describe a service listening on other interfaces?

Quick answers:

1. A permissive codec can ignore an unknown field. A positive case alone would pass with or without the intended restriction. The negative case checks the restriction.
2. Mixins resolve target classes, methods, descriptors, and injection sites at runtime. Forced target loading catches failures that the compiler cannot see; player behaviour still needs a runtime test.
3. The coordinates carry the client's tree layout. Custom model data carries the project's fake-item identity. Rebuilding either without preserving those values can corrupt behaviour without a compile failure.
4. The marker controls download failure handling. A downloaded JAR still participates in class loading and can fail at runtime.
5. Existing saved registries, item components, maps, datapacks, and player progress can follow conversion paths that a new world never uses.
6. Logging a configuration value does not apply it. The socket builder must receive that address, and verification must inspect the listener.
