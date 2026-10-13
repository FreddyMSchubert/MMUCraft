package dev.freddy.chunklocked;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ChunkLocked implements ModInitializer {
    private record Safe(String dimension, Vec3 position) { }

    private static ChunkWorld world;
    private static final Map<UUID, Safe> LAST_SAFE = new HashMap<>();
    private static final int[][] NEIGHBORS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            world = ChunkWorld.load(server);
            ServerLevel overworld = server.overworld();
            world.start(overworld, overworld.getRespawnData().pos());
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "gamerule minecraft:respawn_radius 0");
            command(server, "scoreboard objectives add ChunklockTotalUnlockedChunks dummy");
            command(server, "scoreboard objectives setdisplay list ChunklockTotalUnlockedChunks");
            command(server, "scoreboard players set #global ChunklockTotalUnlockedChunks " + world.totalPaid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (world != null) world.save();
            LAST_SAFE.clear();
            world = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(ChunkLocked::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SAFE.remove(handler.getPlayer().getUUID()));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                world == null || !(level instanceof ServerLevel serverLevel) || world.unlocked(serverLevel, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (world == null || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            ChunkWorld.Cell cell = world.at(serverLevel, hit.getBlockPos());
            if (cell != null && !cell.unlocked) {
                return interactLocked(player, serverLevel, hand, cell);
            }
            if (cell == null) return InteractionResult.FAIL;
            if (player.getItemInHand(hand).getItem() instanceof BlockItem
                    && !world.unlocked(serverLevel, hit.getBlockPos().relative(hit.getDirection()))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (world == null || !(level instanceof ServerLevel serverLevel)
                    || !(entity instanceof Interaction) || !entity.entityTags().contains("chunklocked")) {
                return InteractionResult.PASS;
            }
            ChunkWorld.Cell cell = world.at(serverLevel, entity.blockPosition());
            return cell == null || cell.unlocked ? InteractionResult.PASS
                    : interactLocked(player, serverLevel, hand, cell);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (world == null || !entity.entityTags().contains("chunklocked")) return;
            ChunkWorld.Cell cell = world.at(level, entity.blockPosition());
            if (cell == null || !entity.entityTags().contains(cell.tag())) {
                cell = null;
                int x = Math.floorDiv(entity.blockPosition().getX(), 16);
                int z = Math.floorDiv(entity.blockPosition().getZ(), 16);
                for (int[] offset : NEIGHBORS) {
                    ChunkWorld.Cell neighbor = world.get(level, x + offset[0], z + offset[1]);
                    if (neighbor != null && entity.entityTags().contains(neighbor.tag())) {
                        cell = neighbor;
                        break;
                    }
                }
            }
            if (cell == null || cell.unlocked) entity.discard();
        });
    }

    private static InteractionResult interactLocked(Player player, ServerLevel level,
            InteractionHand hand, ChunkWorld.Cell cell) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DyeItem) {
            String dye = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            cell.colour = dye.substring(0, dye.length() - "_dye".length());
            held.shrink(1);
            world.save();
            ChunkVisuals.recolour(level.getServer(), level, cell);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var required = BuiltInRegistries.ITEM.getValue(Identifier.parse(cell.item));
            serverPlayer.sendSystemMessage(Component.literal("Requires " + cell.remaining + " ")
                    .append(new ItemStack(required).getHoverName()), true);
        }
        return InteractionResult.FAIL;
    }

    private static void tick(MinecraftServer server) {
        if (world == null) return;
        boolean refreshVisuals = server.getTickCount() % 20 == 0;
        boolean scanItems = server.getTickCount() % 2 == 0;
        Set<String> scanned = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = (ServerLevel) player.level();
            String dimension = ChunkWorld.dimension(level);
            BlockPos pos = player.blockPosition();
            Safe safe = LAST_SAFE.get(player.getUUID());
            ChunkWorld.Cell current = world.at(level, pos);
            if (current == null && (safe == null || !safe.dimension.equals(dimension))) {
                world.start(level, pos); // Portal arrivals open their containing chunk, like the source pack.
                current = world.at(level, pos);
            }
            if (current == null || !current.unlocked) {
                Vec3 destination = safe != null && safe.dimension.equals(dimension)
                        ? safe.position : Vec3.atBottomCenterOf(world.origin(level));
                player.teleportTo(level, destination.x, destination.y, destination.z,
                        Set.of(), player.getYRot(), player.getXRot(), true);
                player.setDeltaMovement(Vec3.ZERO);
                continue;
            }
            LAST_SAFE.put(player.getUUID(), new Safe(dimension, player.position()));
            int x = Math.floorDiv(pos.getX(), 16);
            int z = Math.floorDiv(pos.getZ(), 16);
            for (int[] offset : NEIGHBORS) {
                ChunkWorld.Cell cell = world.offer(level, x + offset[0], z + offset[1], pos.getY());
                if (cell == null || cell.unlocked) continue;
                if (refreshVisuals) {
                    ChunkVisuals.ensure(server, level, world, cell, player.getY());
                }
                if (scanItems) {
                    ChunkVisuals.move(level, cell, player.getY());
                }
                if (scanItems && scanned.add(ChunkWorld.key(dimension, cell.x, cell.z))) {
                    payment(server, level, cell, player);
                }
            }
            if (scanItems) {
                for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                        player.getBoundingBox().inflate(32), shot -> shot.getOwner() instanceof ServerPlayer
                                && !world.unlocked(level, shot.blockPosition()))) projectile.discard();
            }
        }
    }

    private static void payment(MinecraftServer server, ServerLevel level, ChunkWorld.Cell cell, ServerPlayer nearby) {
        AABB box = new AABB(cell.x * 16, level.getMinY(), cell.z * 16,
                cell.x * 16 + 16, level.getMaxY(), cell.z * 16 + 16);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box,
                item -> !item.entityTags().contains("cl_processed")
                        && item.getOwner() instanceof ServerPlayer)) {
            ItemStack stack = entity.getItem();
            ServerPlayer payer = (ServerPlayer) entity.getOwner();
            boolean barrier = stack.is(Items.BARRIER);
            if (!barrier && !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(cell.item)) {
                entity.addTag("cl_processed");
                entity.setPos(payer.getX(), payer.getY() + 0.5, payer.getZ());
                entity.setDeltaMovement(Vec3.ZERO);
                entity.setPickUpDelay(0);
                continue;
            }
            int used = barrier ? cell.remaining : Math.min(stack.getCount(), cell.remaining);
            cell.remaining -= used;
            level.playSound(null, entity.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    entity.getX(), entity.getY(), entity.getZ(), 10, 1, 1, 1, 0.1);
            int excess = barrier ? 0 : stack.getCount() - used;
            if (excess == 0) entity.discard();
            else {
                entity.setItem(stack.copyWithCount(excess));
                entity.addTag("cl_processed");
                entity.setPos(payer.getX(), payer.getY() + 0.5, payer.getZ());
                entity.setPickUpDelay(0);
            }
            if (cell.remaining == 0) {
                world.unlock(cell);
                ChunkVisuals.remove(level, cell);
                for (int[] offset : NEIGHBORS) {
                    ChunkWorld.Cell neighbor = world.offer(level, cell.x + offset[0],
                            cell.z + offset[1], cell.y);
                    if (neighbor != null && !neighbor.unlocked) {
                        ChunkVisuals.ensure(server, level, world, neighbor, nearby.getY());
                    }
                }
                command(server, "scoreboard players set #global ChunklockTotalUnlockedChunks " + world.totalPaid());
                command(server, "scoreboard players add " + payer.getGameProfile().name()
                        + " ChunklockTotalUnlockedChunks 1");
                int biomeY = cell.dimension.endsWith("overworld") ? 128 : 64;
                var biome = level.getBiome(new BlockPos(cell.x * 16 + 8, biomeY, cell.z * 16 + 8))
                        .unwrapKey().orElseThrow().identifier();
                String biomeName = Language.DEFAULT_INSTANCE.getOrDefault(
                        "biome." + biome.getNamespace() + "." + biome.getPath());
                String prefix = cell.dimension.endsWith("the_nether") ? "Nether "
                        : cell.dimension.endsWith("the_end") ? "End " : "";
                if (!prefix.isEmpty() && !biomeName.toLowerCase(Locale.ROOT)
                        .startsWith(prefix.trim().toLowerCase(Locale.ROOT))) {
                    biomeName = prefix + biomeName;
                }
                String announcement = payer.getName().getString() + " unlocked " + biomeName
                        + " chunk #" + world.totalPaid() + "!";
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    String message = announcement;
                    if (player.level() == level) {
                        double dx = cell.x * 16 + 8 - player.getX();
                        double dz = cell.z * 16 + 8 - player.getZ();
                        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
                        int direction = Math.floorMod((int) Math.round(Math.atan2(dx, -dz)
                                / (Math.PI / 4)), directions.length);
                        message += " (" + Math.round(Math.hypot(dx, dz)) + "m "
                                + directions[direction] + " of you).";
                    }
                    player.sendSystemMessage(Component.literal(message));
                }
                break;
            }
            world.save();
            ChunkVisuals.update(server, level, world, cell, nearby.getY());
        }
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
