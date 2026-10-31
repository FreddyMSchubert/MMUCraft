package uk.co.httpsmmuminecraftsociety.mainmod.thunder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ThunderRitual {
    private static final String STAND_TAG = "mainmod.thunder_ritual_stand";
    private static final String BAT_TAG = "mainmod.thunder_ritual_bat";
    private static final String COSMETIC_TAG = "mainmod.thunder_ritual_cosmetic";
    private static final String TRIDENT_TAG = "mainmod.thunder_ritual_checked";
    private static final String COSMETIC_ID = "cosmetic-thunder-god";
    private static final String VOICE_NAME = "XXXXXXXXXXXXXXXXXXXX";
    private static final Identifier WISH_STORAGE = Identifier.fromNamespaceAndPath("mainmod", "thunder_ritual_wish");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final List<PendingCopper> PENDING_COPPER = new ArrayList<>();
    private static Wish wish;
    private static final List<Offering> OFFERINGS = List.of(
            new Offering(null, "soul", 1, 1, "I crave a sacrifice of the truest kind. Cast {count} soul before me."),
            new Offering(null, "soul-shard", 3, 7, "Gather what remains of the lost. I demand {count} soul shards."),
            new Offering(Items.AMETHYST_SHARD, null, 4, 8, "Let the earth sing beneath my storm. Bring {count} amethyst shards."),
            new Offering(Items.COPPER_BLOCK.weathering().unaffected(), null, 4, 8,
                    "The heavens require a conductor. Offer {count} copper blocks."),
            new Offering(Items.ECHO_SHARD, null, 2, 3, "The deep remembers every thunderclap. Yield {count} echo shards."),
            new Offering(Items.GHAST_TEAR, null, 1, 1, "Even the Nether must weep for rain. Give me a ghast tear."),
            new Offering(Items.GOLDEN_APPLE, null, 1, 1, "My maw craves a gilded apple. Supply it to me posthaste."),
            new Offering(Items.HEART_OF_THE_SEA, null, 1, 1, "The sea keeps a heart of its own. Surrender {count} heart of the sea."),
            new Offering(null, "beer", 2, 3, "I need fuel for my storm. Pour {count} beer into the tempest."),
            new Offering(null, "golden-nutritional-paste", 1, 1, "My storm hungers for the finest sustenance. Present {count} golden nutritional paste."),
            new Offering(Items.DIAMOND, null, 2, 4, "My lightning is drawn to the purest of gems. Deliver {count} diamonds."),
            new Offering(Items.LAPIS_BLOCK, null, 1, 1, "My magic is weak without the deep blue. Bring me a lapis block."),
            new Offering(null, "coin-10", 2, 4, "I require the currency of mortals. Offer {count} ten-denomination coins."),
            new Offering(null, "duck-rubber", 1, 1, "My life is dreary and dull without the quack. Present a rubber duck to me."),
            new Offering(Items.EMERALD, null, 5, 42, "My storm is drawn to the green of life. Provide {count} emeralds."),
            new Offering(Items.GOLD_INGOT, null, 2, 20, "The shine of gold is pleasing to my eyes. Give me {count} gold ingots.")
    );
    private static final List<String> GREETINGS = lines("greetings");
    private static final List<String> SATISFIED = lines("satisfied");
    private static final List<String> GOODBYES = lines("goodbyes");
    private static final List<String> FULL_MOON = lines("full_moon");
    private static final List<String> FULL_MOON_EVE = lines("full_moon_eve");
    private static final List<String> NO_RAIN = lines("no_rain");
    private static final List<String> COVERED = lines("covered");
    private static final List<String> INVALID_ALTAR = lines("invalid_altar");

    private ThunderRitual() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FakeItems.requireFakeItem(COSMETIC_ID);
            for (Offering offering : OFFERINGS) {
                if (offering.fakeId() != null) FakeItems.requireFakeItem(offering.fakeId());
            }
            wish = loadWish(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID playerId : List.copyOf(SESSIONS.keySet())) end(playerId);
            PENDING_COPPER.clear();
            wish = null;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player.getUUID()));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            for (Session session : List.copyOf(SESSIONS.values())) {
                BlockPos center = session.center;
                if (session.level == level && Math.abs(pos.getX() - center.getX()) <= 2
                        && Math.abs(pos.getZ() - center.getZ()) <= 2
                        && pos.getY() >= center.getY() && pos.getY() <= center.getY() + 4) end(session.playerId);
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            boolean ritualEntity = entity instanceof Bat bat && bat.entityTags().contains(BAT_TAG)
                    || entity instanceof ArmorStand stand && stand.entityTags().contains(COSMETIC_TAG);
            if (!ritualEntity) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer clicker)) return InteractionResult.SUCCESS;
            Session session = SESSIONS.values().stream()
                    .filter(active -> active.entityId.equals(entity.getUUID())
                            || active.cosmeticId.equals(entity.getUUID())).findFirst().orElse(null);
            if (session == null) {
                entity.discard();
                return InteractionResult.SUCCESS;
            }
            say(clicker, session.currentLine);
            return InteractionResult.SUCCESS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand stand && stand.entityTags().contains(STAND_TAG)) {
                stand.discard();
            } else if (entity instanceof ArmorStand stand && stand.entityTags().contains(COSMETIC_TAG)) {
                Session session = SESSIONS.values().stream()
                        .filter(active -> active.cosmeticId.equals(stand.getUUID())).findFirst().orElse(null);
                if (session == null) stand.discard();
                else if (session.level != level) {
                    cancelDisturbed(session);
                    stand.discard();
                }
            } else if (entity instanceof Bat bat && bat.entityTags().contains(BAT_TAG)) {
                Session session = SESSIONS.values().stream()
                        .filter(active -> active.entityId.equals(bat.getUUID())).findFirst().orElse(null);
                if (session == null) bat.discard();
                else if (session.level != level || bat.isLeashed() || bat.isPassenger()
                        || !insideFlightArea(session.center, bat.position())) cancelMovedBat(session, bat);
            } else if (entity instanceof ThrownTrident trident && !trident.entityTags().contains(TRIDENT_TAG)
                    && trident.getOwner() instanceof ServerPlayer player) {
                if (trident.addTag(TRIDENT_TAG)) begin(player);
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSleeping() || !player.level().isThundering()) continue;
                player.stopSleepInBed(true, true);
                player.sendSystemMessage(Component.literal(
                        "You got scared out of bed. Sleeping during thunderstorms isn't possible."), true);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ThunderRitual::tick);
    }

    private static void begin(ServerPlayer player) {
        if (player.getXRot() > -80 || SESSIONS.containsKey(player.getUUID())
                || player.level().dimension() != Level.OVERWORLD || !player.onGround()) return;
        ServerLevel level = (ServerLevel) player.level();
        BlockPos lecternPos = player.getOnPos();
        if (!level.getBlockState(lecternPos).is(Blocks.LECTERN)) return;
        BlockPos center = lecternPos.below();
        if (SESSIONS.values().stream().anyMatch(session -> session.center.equals(center))) return;
        if (!valid(level, center)) {
            say(player, randomLine(INVALID_ALTAR, level));
            return;
        }

        int day = moonDay(level.getOverworldClockTime());
        if (day == 0) {
            say(player, randomLine(FULL_MOON, level));
            return;
        }
        if (day == 7) {
            say(player, randomLine(FULL_MOON_EVE, level));
            return;
        }
        BlockPos sky = center.above(2);
        if (!openSky(level, sky)) {
            reject(level, center, player, randomLine(COVERED, level));
            return;
        }
        if (!level.isRainingAt(sky)) {
            reject(level, center, player, randomLine(NO_RAIN, level));
            return;
        }

        BlockPos rod = selectedRod(center, player.getX(), player.getZ(), player.getYRot());
        Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
        if (bat == null) return;
        bat.setPos(rod.getX() + 0.5, rod.getY() + 2.1, rod.getZ() + 0.5);
        bat.setInvisible(true);
        bat.setResting(false);
        bat.setNoAi(true);
        bat.setNoGravity(true);
        bat.noPhysics = true;
        bat.setPermanentlyInvulnerable(true);
        bat.setPersistenceRequired();
        bat.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        bat.addTag(BAT_TAG);
        ArmorStand cosmetic = new ArmorStand(EntityTypes.ARMOR_STAND, level);
        cosmetic.setPos(bat.position());
        cosmetic.setInvisible(true);
        cosmetic.setNoGravity(true);
        cosmetic.noPhysics = true;
        cosmetic.setPermanentlyInvulnerable(true);
        cosmetic.setNoBasePlate(true);
        cosmetic.setItemSlot(EquipmentSlot.HEAD, FakeItems.createFakeItemStack(COSMETIC_ID, 1));
        cosmetic.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        cosmetic.setYHeadRot(cosmetic.getYRot());
        cosmetic.addTag(COSMETIC_TAG);
        Wish currentWish = getOrCreateWish(level);
        String greeting = randomLine(GREETINGS, level);
        Session session = new Session(player.getUUID(), level, center, bat.getUUID(), cosmetic.getUUID(), currentWish,
                greeting, wishLine(currentWish), level.getServer().getTickCount() + 20);
        SESSIONS.put(player.getUUID(), session);
        if (!level.addFreshEntity(bat)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        if (!level.addFreshEntity(cosmetic) || !cosmetic.startRiding(bat, true, false)) {
            end(player.getUUID());
            cosmetic.discard();
            return;
        }
        bat.positionRider(cosmetic);
        strike(level, rod.getX() + 0.5, rod.getY() + 2, rod.getZ() + 0.5, true);
        say(player, greeting);
    }

    private static boolean valid(ServerLevel level, BlockPos center) {
        if (!level.getBlockState(center).is(Blocks.GOLD_BLOCK)
                || !level.getBlockState(center.above()).is(Blocks.LECTERN)
                || !(level.getBlockEntity(center.above()) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern)) return false;
        ItemStack book = lectern.getBook();
        ItemEnchantments enchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!book.is(Items.ENCHANTED_BOOK) || enchantments.getLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING)) != 1
                || enchantments.size() != 1) return false;
        for (int height = 2; height <= 4; height++)
            if (!level.getBlockState(center.above(height)).isAir()) return false;

        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (x == 0 && z == 0) continue;
            BlockPos pos = center.offset(x, 0, z);
            BlockState state = level.getBlockState(pos);
            for (int height = 1; height <= 4; height++) {
                if (height == 1 && (Math.abs(x) == 2 && Math.abs(z) == 2
                        || Math.abs(x) == 1 && Math.abs(z) == 1)) continue;
                if (!level.getBlockState(pos.above(height)).isAir()) return false;
            }
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                if (!state.is(Blocks.AMETHYST_BLOCK)) return false;
                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    BlockState cluster = level.getBlockState(pos.above());
                    if (!cluster.is(Blocks.AMETHYST_CLUSTER)
                            || cluster.getValue(AmethystClusterBlock.FACING) != Direction.UP
                            || cluster.getValue(AmethystClusterBlock.WATERLOGGED)) return false;
                }
            } else if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                BlockState rod = level.getBlockState(pos.above());
                if (!state.is(Blocks.COPPER_BLOCK.weathering().unaffected())
                        || !rod.is(Blocks.LIGHTNING_ROD.weathering().unaffected())
                        || rod.getValue(LightningRodBlock.FACING) != Direction.UP
                        || rod.getValue(LightningRodBlock.WATERLOGGED)) return false;
            } else {
                Direction inward = inwardFacing(x, z);
                if (!state.is(Blocks.CUT_COPPER_STAIRS.weathering().unaffected())
                        || state.getValue(StairBlock.FACING) != inward
                        || state.getValue(StairBlock.HALF) != Half.BOTTOM
                        || state.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT
                        || state.getValue(StairBlock.WATERLOGGED)) return false;
            }
        }
        return true;
    }

    private static void tick(MinecraftServer server) {
        PENDING_COPPER.removeIf(pending -> {
            if (server.getTickCount() < pending.readyTick || !allChunksLoaded(pending.level, pending.center))
                return false;
            randomizeCopper(pending.level, pending.center);
            return true;
        });
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (SESSIONS.get(session.playerId) != session) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                end(session.playerId);
                continue;
            }
            if (!session.level.isRaining()) {
                end(session.playerId);
                continue;
            }
            boolean loaded = allChunksLoaded(session.level, session.center);
            if (loaded) {
                Entity carrier = session.level.getEntity(session.cosmeticId);
                if (session.level.getEntity(session.entityId) instanceof Bat bat
                        && carrier instanceof ArmorStand cosmetic) {
                    if (bat.isLeashed() || bat.isPassenger() || !insideFlightArea(session.center, bat.position())) {
                        cancelMovedBat(session, bat);
                        continue;
                    }
                    if (cosmetic.getVehicle() != bat && (cosmetic.isPassenger()
                            || cosmetic.distanceToSqr(bat) > 4
                            || !cosmetic.startRiding(bat, true, false))) {
                        cancelDisturbed(session);
                        continue;
                    }
                    session.missingBatTicks = 0;
                    floatBat(session, bat, player, server.getTickCount());
                    bat.positionRider(cosmetic);
                    cosmetic.noPhysics = true;
                    if (player.level() == session.level && player.distanceToSqr(cosmetic) < 100) {
                        cosmetic.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
                        cosmetic.setYHeadRot(cosmetic.getYRot());
                    }
                } else if (++session.missingBatTicks > 100) {
                    say(player, "My herald has gone beyond this altar. This summons is ended.");
                    end(session.playerId);
                    continue;
                }
            }
            if (session.completed) {
                if (server.getTickCount() >= session.nextLineTick) {
                    session.currentLine = randomLine(GOODBYES, session.level);
                    say(player, session.currentLine);
                    end(session.playerId);
                }
                continue;
            }
            if (session.wish != wish) {
                end(session.playerId);
                continue;
            }
            if (!session.offeringAnnounced && server.getTickCount() >= session.nextLineTick) {
                session.offeringAnnounced = true;
                session.currentLine = session.offeringLine;
                say(player, session.currentLine);
            }
            if (!loaded) continue;
            if (!valid(session.level, session.center)) {
                end(session.playerId);
                continue;
            }
            if (session.missingBatTicks > 0) continue;
            if (!session.offeringAnnounced) continue;
            if (player.level() != session.level) continue;
            if (server.getTickCount() % 10 != 0) continue;
            BlockPos center = session.center;
            AABB area = new AABB(center.getX() - 2, center.getY() + 0.5, center.getZ() - 2,
                    center.getX() + 3, center.getY() + 4.5, center.getZ() + 3);
            for (ItemEntity entity : session.level.getEntitiesOfClass(ItemEntity.class, area)) {
                ItemStack stack = entity.getItem();
                if (!session.wish.offering().matches(stack)) continue;
                if (!session.level.isRainingAt(center.above(2))) {
                    reject(session.level, center, player, randomLine(
                            openSky(session.level, center.above(2)) ? NO_RAIN : COVERED, session.level));
                    end(session.playerId);
                    break;
                }
                ServerPlayer submitter = entity.getOwner() instanceof ServerPlayer thrower ? thrower : player;
                int consumed = Math.min(session.wish.remaining, stack.getCount());
                stack.shrink(consumed);
                session.wish.remaining -= consumed;
                if (stack.isEmpty()) entity.discard();
                else entity.setItem(stack);
                session.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY(), entity.getZ(),
                        12, 0.25, 0.3, 0.25, 0.03);
                if (session.wish.remaining == 0) {
                    finish(session, player, submitter, server);
                    break;
                }
                persistWish(server);
                refreshOfferingLines(session.wish);
            }
        }
    }

    private static void floatBat(Session session, Bat bat, ServerPlayer player, int tick) {
        double middleX = session.center.getX() + 0.5;
        double middleZ = session.center.getZ() + 0.5;
        double minY = session.center.getY() + 2.05;
        double maxY = session.center.getY() + 3.65;
        Vec3 current = bat.position();
        if (session.flightTarget == null || tick >= session.nextFlightTargetTick
                || current.distanceToSqr(session.flightTarget) < 0.04) {
            var random = session.level.getRandom();
            session.flightTarget = new Vec3(middleX + (random.nextDouble() - 0.5) * 3.4,
                    minY + random.nextDouble() * (maxY - minY),
                    middleZ + (random.nextDouble() - 0.5) * 3.4);
            session.nextFlightTargetTick = tick + 60 + random.nextInt(80);
        }
        Vec3 offset = session.flightTarget.subtract(current);
        double distance = offset.length();
        if (distance > 0.001) current = current.add(offset.scale(Math.min(0.05, distance) / distance));
        bat.setPos(current);
        bat.setResting(false);
        bat.noPhysics = true;
        bat.setDeltaMovement(Vec3.ZERO);
        if (player.level() == session.level && player.distanceToSqr(bat) < 100)
            bat.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
    }

    private static boolean insideFlightArea(BlockPos center, Vec3 position) {
        return Math.abs(position.x - center.getX() - 0.5) <= 2.25
                && Math.abs(position.z - center.getZ() - 0.5) <= 2.25
                && position.y >= center.getY() + 1.9 && position.y <= center.getY() + 4.2;
    }

    private static void cancelMovedBat(Session session, Bat bat) {
        if (bat.isLeashed()) bat.dropLeash();
        if (bat.isPassenger()) bat.stopRiding();
        cancelDisturbed(session);
        bat.discard();
    }

    private static void cancelDisturbed(Session session) {
        ServerPlayer owner = session.level.getServer().getPlayerList().getPlayer(session.playerId);
        if (owner != null) say(owner, "My herald has been drawn from the altar. This summons is ended.");
        end(session.playerId);
    }

    private static boolean allChunksLoaded(ServerLevel level, BlockPos center) {
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2})
            if (!level.hasChunkAt(center.offset(x, 0, z))) return false;
        return true;
    }

    private static void finish(Session session, ServerPlayer player, ServerPlayer submitter, MinecraftServer server) {
        ServerLevel level = session.level;
        session.completed = true;
        session.currentLine = randomLine(SATISFIED, level);
        session.nextLineTick = server.getTickCount() + 20;
        say(player, session.currentLine);
        wish = null;
        persistWish(server);
        for (Session other : List.copyOf(SESSIONS.values())) {
            if (other == session) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(other.playerId);
            if (owner != null) say(owner, "Another altar has paid my tribute. Call upon me anew.");
            end(other.playerId);
        }
        destroy(level, session.center);
        var weather = level.getWeatherData();
        int stormTime = Math.max(6_000, weather.getRainTime());
        weather.setClearWeatherTime(0);
        weather.setRaining(true);
        weather.setRainTime(stormTime);
        weather.setThundering(true);
        weather.setThunderTime(stormTime);
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            BlockPos rod = session.center.offset(x, 1, z);
            strike(level, rod.getX() + 0.5, rod.getY() + 1, rod.getZ() + 0.5, true);
        }
        if (level.getRandom().nextInt(10) == 0)
            strike((ServerLevel) submitter.level(), submitter.getX(), submitter.getY(), submitter.getZ(), false);
        // Even visual-only lightning clears copper; restore the ritual's random weathering after it ticks.
        PENDING_COPPER.add(new PendingCopper(level, session.center, level.getServer().getTickCount() + 1));
    }

    private static void reject(ServerLevel level, BlockPos center, ServerPlayer player, String line) {
        say(player, line);
        destroy(level, center);
        int count = 1 + level.getRandom().nextInt(5);
        for (int i = 0; i < count; i++) {
            BlockPos spawn = center.offset(level.getRandom().nextInt(5) - 2, 2, level.getRandom().nextInt(5) - 2);
            Vex vex = EntityTypes.VEX.create(level, null, spawn, EntitySpawnReason.EVENT, false, false);
            if (vex == null) continue;
            vex.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            vex.setTarget(player);
            level.addFreshEntity(vex);
        }
        level.playSound(null, center, SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    private static boolean openSky(ServerLevel level, BlockPos pos) {
        return level.canSeeSky(pos)
                && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }

    private static void strike(ServerLevel level, double x, double y, double z, boolean visualOnly) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) return;
        bolt.setPos(x, y, z);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);
    }

    private static void destroy(ServerLevel level, BlockPos center) {
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            level.destroyBlock(center.offset(x, 1, z), false);
        randomizeCopper(level, center);
        BlockPos lecternPos = center.above();
        if (level.getBlockEntity(lecternPos) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern)
            lectern.clearContent();
        level.destroyBlock(lecternPos, false);
        ItemEntity charcoal = new ItemEntity(level, lecternPos.getX() + 0.5, lecternPos.getY() + 0.5,
                lecternPos.getZ() + 0.5, new ItemStack(Items.CHARCOAL, 1 + level.getRandom().nextInt(5)));
        level.addFreshEntity(charcoal);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.getX() + 0.5, center.getY() + 1.5,
                center.getZ() + 0.5, 60, 2, 0.5, 2, 0.1);
    }

    private static void randomizeCopper(ServerLevel level, BlockPos center) {
        Block[] blocks = {
                Blocks.COPPER_BLOCK.weathering().unaffected(), Blocks.COPPER_BLOCK.weathering().exposed(),
                Blocks.COPPER_BLOCK.weathering().weathered(), Blocks.COPPER_BLOCK.weathering().oxidized()
        };
        Block[] stairs = {
                Blocks.CUT_COPPER_STAIRS.weathering().unaffected(), Blocks.CUT_COPPER_STAIRS.weathering().exposed(),
                Blocks.CUT_COPPER_STAIRS.weathering().weathered(), Blocks.CUT_COPPER_STAIRS.weathering().oxidized()
        };
        Block[] rods = {
                Blocks.LIGHTNING_ROD.weathering().unaffected(), Blocks.LIGHTNING_ROD.weathering().exposed(),
                Blocks.LIGHTNING_ROD.weathering().weathered(), Blocks.LIGHTNING_ROD.weathering().oxidized()
        };
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = center.offset(x, 0, z);
            if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                oxidize(level, pos, blocks);
                oxidize(level, pos.above(), rods);
            } else if (Math.abs(x) == 2 || Math.abs(z) == 2) oxidize(level, pos, stairs);
        }
    }

    private static void oxidize(ServerLevel level, BlockPos pos, Block[] states) {
        BlockState old = level.getBlockState(pos);
        for (Block state : states) {
            if (old.is(state)) {
                level.setBlock(pos, states[level.getRandom().nextInt(states.length)].withPropertiesOf(old), 3);
                return;
            }
        }
    }

    private static void end(UUID playerId) {
        Session session = SESSIONS.remove(playerId);
        if (session == null) return;
        for (UUID entityId : List.of(session.cosmeticId, session.entityId)) {
            for (ServerLevel level : session.level.getServer().getAllLevels()) {
                Entity entity = level.getEntity(entityId);
                if (entity instanceof Bat bat && bat.isLeashed()) bat.dropLeash();
                if (entity != null) {
                    if (entity.isPassenger()) entity.stopRiding();
                    entity.discard();
                }
            }
        }
    }

    static BlockPos selectedRod(BlockPos center, double playerX, double playerZ, float yaw) {
        double radians = Math.toRadians(yaw);
        double lookX = -Math.sin(radians);
        double lookZ = Math.cos(radians);
        BlockPos best = null;
        double bestAlignment = Double.NEGATIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            BlockPos rod = center.offset(x, 0, z);
            double dx = rod.getX() + 0.5 - playerX;
            double dz = rod.getZ() + 0.5 - playerZ;
            double distance = Math.hypot(dx, dz);
            double alignment = (lookX * dx + lookZ * dz) / distance;
            if (alignment > bestAlignment + 1e-12
                    || Math.abs(alignment - bestAlignment) <= 1e-12 && distance < bestDistance) {
                best = rod;
                bestAlignment = alignment;
                bestDistance = distance;
            }
        }
        return best;
    }

    static Direction inwardFacing(int x, int z) {
        return x == -2 ? Direction.EAST : x == 2 ? Direction.WEST
                : z == -2 ? Direction.SOUTH : Direction.NORTH;
    }

    static int moonDay(long clockTime) {
        return (int) Math.floorMod(Math.floorDiv(clockTime, 24_000L), 8L);
    }

    static String countWord(int count) {
        String[] words = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
        return count >= 1 && count <= 10 ? words[count] : Integer.toString(count);
    }

    private static Wish getOrCreateWish(ServerLevel level) {
        if (wish == null) {
            int index = level.getRandom().nextInt(OFFERINGS.size());
            Offering offering = OFFERINGS.get(index);
            int count = offering.min() + level.getRandom().nextInt(offering.max() - offering.min() + 1);
            wish = new Wish(index, count);
            persistWish(level.getServer());
        }
        return wish;
    }

    private static Wish loadWish(MinecraftServer server) {
        CompoundTag saved = server.getCommandStorage().get(WISH_STORAGE);
        if (!saved.contains("offering")) return null;
        int index = saved.getIntOr("offering", -1);
        int remaining = saved.getIntOr("remaining", -1);
        if (index < 0 || index >= OFFERINGS.size() || remaining < 1 || remaining > OFFERINGS.get(index).max())
            throw new IllegalStateException("Invalid saved thunder ritual wish: " + saved);
        return new Wish(index, remaining);
    }

    private static void persistWish(MinecraftServer server) {
        CompoundTag saved = new CompoundTag();
        if (wish != null) {
            saved.putInt("offering", wish.offeringIndex);
            saved.putInt("remaining", wish.remaining);
        }
        server.getCommandStorage().set(WISH_STORAGE, saved);
    }

    private static String wishLine(Wish wish) {
        return wish.offering().words().replace("{count}", countWord(wish.remaining));
    }

    private static void refreshOfferingLines(Wish wish) {
        String line = wishLine(wish);
        for (Session session : SESSIONS.values()) {
            if (session.wish != wish || session.completed) continue;
            session.offeringLine = line;
            if (session.offeringAnnounced) session.currentLine = line;
        }
    }

    private static void say(ServerPlayer player, String words) {
        player.sendSystemMessage(Component.literal("<").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(VOICE_NAME.substring(0, 7)).withStyle(ChatFormatting.OBFUSCATED))
                .append(" ")
                .append(Component.literal(VOICE_NAME.substring(7, 17)).withStyle(ChatFormatting.OBFUSCATED))
                .append(" ")
                .append(Component.literal(VOICE_NAME.substring(17)).withStyle(ChatFormatting.OBFUSCATED))
                .append("> ")
                .append(Component.literal(words).withStyle(ChatFormatting.GOLD)));
    }

    private static String randomLine(List<String> lines, ServerLevel level) {
        return lines.get(level.getRandom().nextInt(lines.size()));
    }

    private static List<String> lines(String name) {
        String path = "/data/mainmod/thunder_ritual/" + name + ".txt";
        try (var stream = ThunderRitual.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing thunder ritual lines: " + path);
            List<String> lines = new ArrayList<>();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(lines::add);
            }
            if (lines.isEmpty()) throw new IllegalStateException("Empty thunder ritual lines: " + path);
            return List.copyOf(lines);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read thunder ritual lines: " + path, e);
        }
    }

    private record Offering(Item item, String fakeId, int min, int max, String words) {
        Offering {
            if ((item == null) == (fakeId == null) || min < 1 || max < min
                    || min != max && !words.contains("{count}"))
                throw new IllegalArgumentException("Invalid thunder ritual offering: " + words);
        }

        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) && FakeItems.getFakeItemFromStack(stack) == null
                    : FakeItems.getFakeItemFromStack(stack) == FakeItems.requireFakeItem(fakeId);
        }
    }

    private static final class Wish {
        final int offeringIndex;
        int remaining;

        Wish(int offeringIndex, int remaining) {
            this.offeringIndex = offeringIndex;
            this.remaining = remaining;
        }

        Offering offering() {
            return OFFERINGS.get(offeringIndex);
        }
    }

    private static final class Session {
        final UUID playerId;
        final ServerLevel level;
        final BlockPos center;
        final UUID entityId;
        final UUID cosmeticId;
        final Wish wish;
        Vec3 flightTarget;
        int nextFlightTargetTick;
        int missingBatTicks;
        String offeringLine;
        int nextLineTick;
        boolean offeringAnnounced;
        boolean completed;
        String currentLine;

        Session(UUID playerId, ServerLevel level, BlockPos center, UUID entityId, UUID cosmeticId, Wish wish,
                String greeting, String offeringLine, int offeringAtTick) {
            this.playerId = playerId;
            this.level = level;
            this.center = center;
            this.entityId = entityId;
            this.cosmeticId = cosmeticId;
            this.wish = wish;
            this.currentLine = greeting;
            this.offeringLine = offeringLine;
            this.nextLineTick = offeringAtTick;
        }
    }

    private record PendingCopper(ServerLevel level, BlockPos center, int readyTick) {}
}
