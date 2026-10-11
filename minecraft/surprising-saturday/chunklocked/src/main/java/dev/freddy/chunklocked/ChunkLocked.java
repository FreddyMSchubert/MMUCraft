package dev.freddy.chunklocked;

import java.util.HashMap;
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
            for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
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
                item -> !item.entityTags().contains("cl_processed"))) {
            ItemStack stack = entity.getItem();
            ServerPlayer payer = entity.getOwner() instanceof ServerPlayer owner ? owner : nearby;
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
                command(server, "scoreboard players set #global ChunklockTotalUnlockedChunks " + world.totalPaid());
                command(server, "scoreboard players add " + payer.getGameProfile().name()
                        + " ChunklockTotalUnlockedChunks 1");
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal(payer.getName().getString()
                            + " unlocked chunk #" + world.totalPaid() + "!"));
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
