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
import net.minecraft.network.chat.Component;
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
    private static final String TRIDENT_TAG = "mainmod.thunder_ritual_checked";
    private static final String COSMETIC_ID = "cosmetic-warden-antlers";
    private static final String VOICE_NAME = "XXXXXXXXXXXXXXXXXXXX";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final List<PendingCopper> PENDING_COPPER = new ArrayList<>();
    private static final List<Offering> OFFERINGS = List.of(
            new Offering(null, "soul", 1, 1, "I crave a sacrifice of the truest kind. Cast {count} soul before me."),
            new Offering(null, "soul-shard", 3, 7, "Gather what remains of the lost. I demand {count} soul shards."),
            new Offering(Items.AMETHYST_SHARD, null, 4, 8, "Let the earth sing beneath my storm. Bring {count} amethyst shards."),
            new Offering(Items.COPPER_INGOT, null, 3, 9, "The heavens require a conductor. Offer {count} copper ingots."),
            new Offering(Items.ECHO_SHARD, null, 2, 4, "The deep remembers every thunderclap. Yield {count} echo shards."),
            new Offering(Items.GHAST_TEAR, null, 2, 5, "Even the Nether must weep for rain. Give me {count} ghast tears."),
            new Offering(Items.HEART_OF_THE_SEA, null, 1, 1, "The sea keeps a heart of its own. Surrender {count} heart of the sea."),
            new Offering(null, "charm-lucky-charm", 1, 1, "Fortune has smiled on you long enough. Lay down {count} Lucky Charm.")
    );
    private static final List<String> GREETINGS = lines("greetings");
    private static final List<String> SATISFIED = lines("satisfied");
    private static final List<String> GOODBYES = lines("goodbyes");

    private ThunderRitual() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FakeItems.requireFakeItem(COSMETIC_ID);
            for (Offering offering : OFFERINGS) {
                if (offering.fakeId() != null) FakeItems.requireFakeItem(offering.fakeId());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID playerId : List.copyOf(SESSIONS.keySet())) end(playerId);
            PENDING_COPPER.clear();
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
            if (!(player instanceof ServerPlayer clicker) || !(entity instanceof ArmorStand stand)
                    || !stand.entityTags().contains(STAND_TAG)) return InteractionResult.PASS;
            Session session = SESSIONS.values().stream()
                    .filter(active -> active.standId.equals(stand.getUUID())).findFirst().orElse(null);
            if (session == null) {
                stand.discard();
                return InteractionResult.SUCCESS;
            }
            say(clicker, session.currentLine);
            return InteractionResult.SUCCESS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand stand && stand.entityTags().contains(STAND_TAG)) {
                if (SESSIONS.values().stream().noneMatch(session -> session.standId.equals(stand.getUUID()))) stand.discard();
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
        if (!valid(level, center) || SESSIONS.values().stream().anyMatch(session -> session.center.equals(center))) return;

        int day = moonDay(level.getOverworldClockTime());
        if (day == 0) {
            say(player, "Silence. I am busy celebrating the gorgeous full moon.");
            return;
        }
        if (day == 7) {
            say(player, "Silence. The gorgeous full moon is coming up.");
            return;
        }

        Vec3 look = player.getLookAngle();
        BlockPos rod = center.offset(cornerOffset(look.x), 0, cornerOffset(look.z));
        ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, level);
        stand.setPos(rod.getX() + 0.5, rod.getY() + 2, rod.getZ() + 0.5);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setItemSlot(EquipmentSlot.HEAD, FakeItems.createFakeItemStack(COSMETIC_ID, 1));
        stand.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        stand.setYHeadRot(stand.getYRot());
        stand.addTag(STAND_TAG);
        Offering offering = OFFERINGS.get(level.getRandom().nextInt(OFFERINGS.size()));
        int count = offering.min() + level.getRandom().nextInt(offering.max() - offering.min() + 1);
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), level, center, stand.getUUID(), offering, count,
                level.isRaining()));
        if (!level.addFreshEntity(stand)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        level.playSound(null, center, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.2F, 0.65F);
        say(player, randomLine(GREETINGS, level));
        Session session = SESSIONS.get(player.getUUID());
        session.currentLine = offering.words().replace("{count}", countWord(count));
        say(player, session.currentLine);
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
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                end(session.playerId);
                continue;
            }
            if (session.level.isRaining()) session.hasSeenRain = true;
            if (session.hasSeenRain && !session.level.isRaining()) {
                end(session.playerId);
                continue;
            }
            if (!allChunksLoaded(session.level, session.center)) continue;
            if (!valid(session.level, session.center)) {
                end(session.playerId);
                continue;
            }
            if (player.level() != session.level) continue;
            if (server.getTickCount() % 10 != 0) continue;
            BlockPos center = session.center;
            AABB area = new AABB(center.getX() - 2, center.getY() + 0.5, center.getZ() - 2,
                    center.getX() + 3, center.getY() + 4.5, center.getZ() + 3);
            for (ItemEntity entity : session.level.getEntitiesOfClass(ItemEntity.class, area)) {
                ItemStack stack = entity.getItem();
                if (!session.offering.matches(stack)) continue;
                ServerPlayer submitter = entity.getOwner() instanceof ServerPlayer thrower ? thrower : player;
                int consumed = Math.min(session.remaining, stack.getCount());
                stack.shrink(consumed);
                session.remaining -= consumed;
                if (stack.isEmpty()) entity.discard();
                else entity.setItem(stack);
                session.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY(), entity.getZ(),
                        12, 0.25, 0.3, 0.25, 0.03);
                if (session.remaining == 0) {
                    finish(session, player, submitter);
                    break;
                }
            }
        }
    }

    private static boolean allChunksLoaded(ServerLevel level, BlockPos center) {
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2})
            if (!level.hasChunkAt(center.offset(x, 0, z))) return false;
        return true;
    }

    private static void finish(Session session, ServerPlayer player, ServerPlayer submitter) {
        ServerLevel level = session.level;
        boolean exposedToRain = level.isRainingAt(player.blockPosition().above());
        say(player, randomLine(SATISFIED, level));
        say(player, exposedToRain ? randomLine(GOODBYES, level)
                : "You hid from my sky. Take my wrath instead of my thunder.");
        destroy(level, session.center);
        if (exposedToRain) {
            var weather = level.getWeatherData();
            weather.setClearWeatherTime(0);
            weather.setRaining(true);
            weather.setRainTime(12_000);
            weather.setThundering(true);
            weather.setThunderTime(12_000);
            for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
                BlockPos rod = session.center.offset(x, 1, z);
                strike(level, rod.getX() + 0.5, rod.getY() + 1, rod.getZ() + 0.5, true);
            }
            if (level.getRandom().nextInt(10) == 0)
                strike(level, submitter.getX(), submitter.getY(), submitter.getZ(), false);
            // Even visual-only lightning clears copper; restore the ritual's random weathering after it ticks.
            PENDING_COPPER.add(new PendingCopper(level, session.center, level.getServer().getTickCount() + 1));
        } else {
            int count = 1 + level.getRandom().nextInt(5);
            for (int i = 0; i < count; i++) {
                BlockPos spawn = session.center.offset(level.getRandom().nextInt(5) - 2, 2, level.getRandom().nextInt(5) - 2);
                Vex vex = EntityTypes.VEX.create(level, null, spawn, EntitySpawnReason.EVENT, false, false);
                if (vex == null) continue;
                vex.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                vex.setTarget(player);
                level.addFreshEntity(vex);
            }
            level.playSound(null, session.center, SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1.5F, 0.8F);
        }
        end(session.playerId);
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
        Entity stand = session.level.getEntity(session.standId);
        if (stand != null) stand.discard();
    }

    static int cornerOffset(double direction) {
        return direction < 0 ? -2 : 2;
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
            if ((item == null) == (fakeId == null) || min < 1 || max < min || !words.contains("{count}"))
                throw new IllegalArgumentException("Invalid thunder ritual offering: " + words);
        }

        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) && FakeItems.getFakeItemFromStack(stack) == null
                    : FakeItems.getFakeItemFromStack(stack) == FakeItems.requireFakeItem(fakeId);
        }
    }

    private static final class Session {
        final UUID playerId;
        final ServerLevel level;
        final BlockPos center;
        final UUID standId;
        final Offering offering;
        boolean hasSeenRain;
        int remaining;
        String currentLine;

        Session(UUID playerId, ServerLevel level, BlockPos center, UUID standId, Offering offering, int remaining,
                boolean startedRaining) {
            this.playerId = playerId;
            this.level = level;
            this.center = center;
            this.standId = standId;
            this.offering = offering;
            this.hasSeenRain = startedRaining;
            this.remaining = remaining;
        }
    }

    private record PendingCopper(ServerLevel level, BlockPos center, int readyTick) {}
}
