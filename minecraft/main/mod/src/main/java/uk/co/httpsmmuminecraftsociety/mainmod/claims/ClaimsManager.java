package uk.co.httpsmmuminecraftsociety.mainmod.claims;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ClaimData;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ClaimsSnapshot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimsManager {
    public static final int HOSTILE = 1 << 0;
    public static final int NEUTRAL = 1 << 1;
    public static final int PASSIVE = 1 << 2;
    public static final int PVP = 1 << 3;
    public static final int CONTROLS = 1 << 4;
    public static final int DOORS = 1 << 5;
    public static final int BREAK = 1 << 6;
    public static final int PLACE = 1 << 7;
    public static final int CONTAINERS = 1 << 8;
    public static final int OTHER_BLOCKS = 1 << 9;
    public static final int BEDS = 1 << 10;
    public static final int TRADING = 1 << 11;
    public static final int MOB_INTERACTION = 1 << 12;
    public static final int EXPLOSIONS = 1 << 13;
    public static final int GLIDING = 1 << 14;
    public static final int ITEM_PICKUP = 1 << 15;
    public static final int PROJECTILES = 1 << 16;
    public static final int NETHER_PORTALS = 1 << 17;
    public static final int MINECARTS = 1 << 18;
    public static final int OTHER_ENTITY_DAMAGE = 1 << 19;
    private static final UUID SERVER_ADMIN_UUID = UUID.fromString("8580f9f8-30c4-4b83-a66c-ac52ac6d5b0b");
    private static final double MINIMUM_HEADING_LIGHTNESS = 0.6;
    private static final ThreadLocal<ArrayDeque<ServerPlayer>> ACTORS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> FIRE_TICKS = ThreadLocal.withInitial(() -> 0);
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static final Map<UUID, String> BOSS_BAR_STATES = new HashMap<>();
    private static final Map<UUID, Long> LAST_DENIED_MESSAGE = new HashMap<>();
    private static volatile Map<ClaimKey, Claim> claims = Map.of();
    private static volatile Set<UUID> committeeUuids = Set.of();
    private static volatile boolean ready;

    private ClaimsManager() {
    }

    public static void init() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                checkDirect(player, level, pos, BREAK) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            int permission = player.getItemInHand(hand).getItem() instanceof MinecartItem
                    && level.getBlockState(pos).is(BlockTags.RAILS) ? MINECARTS
                    : player.isShiftKeyDown() && player.getItemInHand(hand).getItem() instanceof BlockItem
                    ? PLACE : blockPermission(level, pos);
            if (!checkDirect(player, level, pos, permission)) return InteractionResult.FAIL;
            if (player instanceof ServerPlayer serverPlayer) {
                clearStaleOccupiedBed(serverPlayer, level, pos);
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                checkDirect(player, level, pos, BREAK));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                checkDirect(player, level, entity.blockPosition(), entity instanceof LivingEntity living
                        ? damagePermission(living) : entity instanceof AbstractMinecart ? MINECARTS
                        : OTHER_ENTITY_DAMAGE)
                        ? InteractionResult.PASS : InteractionResult.FAIL);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                checkDirect(player, level, entity.blockPosition(), entity instanceof AbstractVillager
                        ? TRADING : entity instanceof AbstractMinecart ? MINECARTS
                        : entity instanceof Cushion ? BEDS : MOB_INTERACTION)
                        ? InteractionResult.PASS : InteractionResult.FAIL);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ClaimsManager::allowPlayerDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeBossBar(handler.getPlayer()));
    }

    public static void reset() {
        claims = Map.of();
        committeeUuids = Set.of();
        ready = false;
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
        BOSS_BAR_STATES.clear();
        LAST_DENIED_MESSAGE.clear();
        ACTORS.remove();
        FIRE_TICKS.remove();
    }

    public static void apply(ClaimsSnapshot snapshot) {
        Set<UUID> nextCommittee = new HashSet<>();
        for (String uuid : snapshot.getCommitteeUuidsList()) {
            try { nextCommittee.add(parseUuid(uuid)); }
            catch (IllegalArgumentException ignored) { MainMod.LOGGER.warn("Ignored invalid committee UUID"); }
        }
        committeeUuids = Set.copyOf(nextCommittee);
        Map<ClaimKey, Claim> next = new HashMap<>();
        for (ClaimData data : snapshot.getClaimsList()) {
            try {
                long width = (long) data.getChunkXEnd() - data.getChunkX() + 1;
                long height = (long) data.getChunkZEnd() - data.getChunkZ() + 1;
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || width * height > 4096
                        || (!data.getIsServerClaim() && (width != 1 || height != 1))
                        || Math.abs((long) data.getChunkX()) > 1_875_000
                        || Math.abs((long) data.getChunkZ()) > 1_875_000
                        || Math.abs((long) data.getChunkXEnd()) > 1_875_000
                        || Math.abs((long) data.getChunkZEnd()) > 1_875_000) {
                    MainMod.LOGGER.warn("Ignored claim {} because its chunk area is invalid", data.getId());
                    continue;
                }
                UUID ownerUuid = parseUuid(data.getOwnerUuid());
                Set<UUID> members = new HashSet<>();
                if (!data.getIsServerClaim()) members.add(ownerUuid);
                for (String memberUuid : data.getMemberUuidsList()) {
                    members.add(parseUuid(memberUuid));
                }
                String name = data.getName().strip();
                if (name.isEmpty()) name = "My claim";
                if (name.length() > 50) name = name.substring(0, 50);
                Claim claim = new Claim(
                        data.getId(), ownerUuid, data.getOwnerName(), name,
                        parseColor(data.getColorHex()), parseColor(data.getOwnerColorHex()),
                        data.getHasCustomColor(), data.getIsServerClaim(), Set.copyOf(members),
                        data.getOutsiderPermissions()
                );
                for (int x = data.getChunkX(); x <= data.getChunkXEnd(); x++) {
                    for (int z = data.getChunkZ(); z <= data.getChunkZEnd(); z++) {
                        next.put(new ClaimKey(data.getDimension(), ChunkPos.pack(x, z)), claim);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                MainMod.LOGGER.warn("Ignored claim {} because it has an invalid Minecraft UUID", data.getId());
            }
        }
        claims = Map.copyOf(next);
        ready = true;
        BOSS_BAR_STATES.clear();
    }

    public static boolean isCommittee(UUID playerUuid) {
        return committeeUuids.contains(playerUuid);
    }

    public static void updateOwnerColor(UUID ownerUuid, int color) {
        Map<ClaimKey, Claim> next = new HashMap<>(claims);
        next.replaceAll((key, claim) -> claim.ownerUuid().equals(ownerUuid)
                ? new Claim(claim.id(), claim.ownerUuid(), claim.ownerName(), claim.name(),
                        claim.hasCustomColor() ? claim.colorRgb() : color, color, claim.hasCustomColor(),
                        claim.serverClaim(), claim.members(), claim.outsiderPermissions())
                : claim);
        claims = Map.copyOf(next);
        BOSS_BAR_STATES.clear();
    }

    public static void beginPlayerAction(ServerPlayer player) {
        ACTORS.get().push(player);
    }

    public static void endPlayerAction() {
        ArrayDeque<ServerPlayer> actors = ACTORS.get();
        if (!actors.isEmpty()) actors.pop();
        if (actors.isEmpty()) ACTORS.remove();
    }

    public static void beginFireTick() {
        FIRE_TICKS.set(FIRE_TICKS.get() + 1);
    }

    public static void endFireTick() {
        int depth = FIRE_TICKS.get() - 1;
        if (depth <= 0) FIRE_TICKS.remove();
        else FIRE_TICKS.set(depth);
    }

    public static boolean allowBlockMutation(Level level, BlockPos pos, BlockState newState) {
        ArrayDeque<ServerPlayer> actors = ACTORS.get();
        if (!actors.isEmpty()) {
            int permission = level.getBlockState(pos).isAir() ? PLACE
                    : newState.isAir() ? BREAK
                    : level.getBlockState(pos).getBlock() == newState.getBlock()
                    ? blockPermission(level, pos) : PLACE;
            return canPerform(actors.peek(), level, pos, permission);
        }

        if (FIRE_TICKS.get() > 0 && (!ready || isClaimed(level, pos))) {
            return level.getBlockState(pos).getBlock() instanceof BaseFireBlock;
        }
        if ((!ready || isClaimed(level, pos)) && newState.getBlock() instanceof BaseFireBlock) return false;
        return true;
    }

    public static boolean allowBlockRemoval(Level level, BlockPos pos) {
        return allowBlockMutation(level, pos, level.getFluidState(pos).createLegacyBlock());
    }

    public static boolean allowProjectileHere(Entity entity) {
        return !(entity instanceof Projectile projectile)
                || allowProjectileAt(projectile, entity.blockPosition());
    }

    public static boolean allowProjectileAt(Projectile projectile, BlockPos pos) {
        return canProjectileEnter(projectile, projectile.level(), pos);
    }

    public static void rejectProjectile(Projectile projectile) {
        if (projectile.isRemoved()) return;
        if (projectile instanceof ThrownTrident trident
                && trident.pickup == AbstractArrow.Pickup.ALLOWED) {
            ItemStack item = trident.getPickupItemStackOrigin().copy();
            if (!item.isEmpty()) {
                if (trident.getOwner() instanceof ServerPlayer player) {
                    if (!player.hasInfiniteMaterials()) {
                        player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
                    }
                } else if (trident.level() instanceof ServerLevel level) {
                    trident.spawnAtLocation(level, item);
                }
            }
        }
        projectile.discard();
    }

    public static boolean canAccess(ServerPlayer player, Level level, BlockPos pos) {
        if (!ready) return isOperator(player);
        Claim claim = claimAt(level, pos);
        return claim == null || canAccessClaim(player, claim);
    }

    public static boolean canPerform(ServerPlayer player, Level level, BlockPos pos, int permission) {
        if (!ready) return isOperator(player);
        Claim claim = claimAt(level, pos);
        return claim == null || canAccessClaim(player, claim)
                || (claim.outsiderPermissions() & permission) != 0;
    }

    public static boolean allowExplosionDamage(Level level, BlockPos pos, Entity source) {
        return source instanceof ServerPlayer player && canPerform(player, level, pos, EXPLOSIONS);
    }

    public static boolean isClaimed(Level level, BlockPos pos) {
        return claimAt(level, pos) != null;
    }

    public static boolean isReady() {
        return ready;
    }

    public static String claimIdAt(Level level, BlockPos pos) {
        Claim claim = claimAt(level, pos);
        return claim == null ? null : claim.id();
    }

    public static boolean crossesClaimBoundary(Level level, BlockPos from, BlockPos to) {
        return !Objects.equals(claimIdAt(level, from), claimIdAt(level, to));
    }

    public static BlockHitResult deniedProjectileEntry(Projectile projectile, Vec3 start, Vec3 movement) {
        if (!ready) {
            if (allowProjectileAt(projectile, BlockPos.containing(start))) return null;
            return new BlockHitResult(
                    start,
                    Direction.getApproximateNearest(movement).getOpposite(),
                    BlockPos.containing(start),
                    false
            );
        }
        if (movement.lengthSqr() == 0) return null;
        Vec3 end = start.add(movement);
        BlockPos blockedPos = BlockGetter.traverseBlocks(
                start,
                end,
                projectile,
                (ignored, pos) -> allowProjectileAt(projectile, pos)
                        ? null : pos.immutable(),
                ignored -> null
        );
        if (blockedPos == null) return null;

        Vec3 point = BlockPos.containing(start).equals(blockedPos)
                ? start
                : new AABB(blockedPos).clip(start, end).orElse(start);
        Direction face = Direction.getApproximateNearest(movement).getOpposite();
        return new BlockHitResult(point, face, blockedPos, false);
    }

    public static void tickBossBars(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerBossEvent existingBar = BOSS_BARS.get(player.getUUID());
            if (existingBar != null && !existingBar.getPlayers().contains(player)) {
                existingBar.removeAllPlayers();
                existingBar.addPlayer(player);
                BOSS_BAR_STATES.remove(player.getUUID());
            }

            Claim claim = claimAt(player.level(), player.blockPosition());
            String state = claim == null ? "" : claim.id();
            if (state.equals(BOSS_BAR_STATES.get(player.getUUID()))) continue;
            BOSS_BAR_STATES.put(player.getUUID(), state);

            ServerBossEvent bar = BOSS_BARS.computeIfAbsent(player.getUUID(), ignored -> {
                ServerBossEvent created = new ServerBossEvent(
                        UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS
                );
                created.setProgress(0);
                created.addPlayer(player);
                return created;
            });

            if (claim == null) {
                bar.setVisible(false);
                continue;
            }

            Component name = Component.literal(claim.name())
                    .withStyle(Style.EMPTY.withColor(withMinimumLightness(claim.colorRgb())));
            bar.setName(claim.serverClaim()
                    ? name
                    : Component.literal(claim.ownerName() + "'s claim: ")
                            .withStyle(Style.EMPTY.withColor(withMinimumLightness(claim.ownerColorRgb())))
                            .append(name));
            bar.setVisible(true);
        }
    }

    private static boolean checkDirect(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos, int permission) {
        if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) return true;
        boolean allowed = canPerform(serverPlayer, level, pos, permission);
        if (!allowed) notifyDenied(serverPlayer);
        return allowed;
    }

    private static void clearStaleOccupiedBed(ServerPlayer player, Level level, BlockPos clickedPos) {
        BlockState clicked = level.getBlockState(clickedPos);
        if (!(clicked.getBlock() instanceof AbstractBedBlock)) return;
        BlockPos headPos = clicked.getValue(AbstractBedBlock.PART) == BedPart.HEAD
                ? clickedPos : clickedPos.relative(clicked.getValue(AbstractBedBlock.FACING));
        BlockState head = level.getBlockState(headPos);
        if (!head.is(clicked.getBlock()) || head.getValue(AbstractBedBlock.PART) != BedPart.HEAD
                || !head.getValue(AbstractBedBlock.OCCUPIED) || !isClaimed(level, headPos)
                || !canPerform(player, level, headPos, BEDS)) return;
        boolean sleeperPresent = !level.getEntitiesOfClass(LivingEntity.class, new AABB(headPos).inflate(2),
                entity -> entity.isSleeping() && entity.getSleepingPos().filter(headPos::equals).isPresent()).isEmpty();
        if (!sleeperPresent) {
            level.setBlockAndUpdate(headPos, head.setValue(AbstractBedBlock.OCCUPIED, false));
        }
    }

    private static boolean allowPlayerDamage(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        boolean allowed = canPerform(player, target.level(), target.blockPosition(), damagePermission(target));
        if (!allowed) notifyDenied(player);
        return allowed;
    }

    private static void notifyDenied(ServerPlayer player) {
        long now = System.currentTimeMillis();
        if (now - LAST_DENIED_MESSAGE.getOrDefault(player.getUUID(), 0L) < 750) return;
        LAST_DENIED_MESSAGE.put(player.getUUID(), now);
        player.sendOverlayMessage(Component.literal(ready ? "This chunk is protected." : "Claims are still loading."));
    }

    private static Claim claimAt(Level level, BlockPos pos) {
        return claims.get(new ClaimKey(level.dimension().identifier().toString(), ChunkPos.pack(pos)));
    }

    private static boolean canProjectileEnter(Projectile projectile, Level level, BlockPos pos) {
        Entity owner = projectile.getOwner();
        ServerPlayer player = owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (!ready) return player != null && isOperator(player);
        Claim claim = claimAt(level, pos);
        return claim == null || player != null && canPerform(player, level, pos, PROJECTILES);
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && server.getPlayerList().isOp(player.nameAndId());
    }

    private static boolean canAccessClaim(ServerPlayer player, Claim claim) {
        return claim.serverClaim() ? SERVER_ADMIN_UUID.equals(player.getUUID())
                : claim.members().contains(player.getUUID()) || isOperator(player);
    }

    private static int damagePermission(LivingEntity target) {
        if (target instanceof ServerPlayer) return PVP;
        if (!(target instanceof Mob)) return OTHER_ENTITY_DAMAGE;
        if (target instanceof NeutralMob) return NEUTRAL;
        return target.getType().getCategory() == MobCategory.MONSTER ? HOSTILE : PASSIVE;
    }

    private static int blockPermission(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var block = state.getBlock();
        if (block instanceof ButtonBlock || block instanceof LeverBlock) return CONTROLS;
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) return DOORS;
        if (block instanceof AbstractBedBlock) return BEDS;
        if (block instanceof EnderChestBlock || level.getBlockEntity(pos) instanceof Container) return CONTAINERS;
        return state.getMenuProvider(level, pos) != null ? OTHER_BLOCKS : PLACE;
    }

    private static UUID parseUuid(String value) {
        String compact = value.replace("-", "");
        if (compact.length() != 32) throw new IllegalArgumentException("Invalid Minecraft UUID");
        return new UUID(
                Long.parseUnsignedLong(compact.substring(0, 16), 16),
                Long.parseUnsignedLong(compact.substring(16), 16)
        );
    }

    private static int parseColor(String value) {
        if (value.length() != 7 || value.charAt(0) != '#') return 0xFFD166;
        try {
            return Integer.parseInt(value.substring(1), 16);
        } catch (NumberFormatException ignored) {
            return 0xFFD166;
        }
    }

    private static int withMinimumLightness(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        double lightness = (Math.max(red, Math.max(green, blue)) + Math.min(red, Math.min(green, blue))) / 510.0;
        if (lightness >= MINIMUM_HEADING_LIGHTNESS) return rgb;
        double whiteBlend = (MINIMUM_HEADING_LIGHTNESS - lightness) / (1 - lightness);
        red = (int) Math.round(red + (255 - red) * whiteBlend);
        green = (int) Math.round(green + (255 - green) * whiteBlend);
        blue = (int) Math.round(blue + (255 - blue) * whiteBlend);
        return (red << 16) | (green << 8) | blue;
    }

    private static void removeBossBar(ServerPlayer player) {
        ServerBossEvent bar = BOSS_BARS.remove(player.getUUID());
        if (bar != null) bar.removePlayer(player);
        BOSS_BAR_STATES.remove(player.getUUID());
        LAST_DENIED_MESSAGE.remove(player.getUUID());
    }

    private record ClaimKey(String dimension, long chunk) {
    }

    private record Claim(String id, UUID ownerUuid, String ownerName, String name, int colorRgb,
                         int ownerColorRgb, boolean hasCustomColor, boolean serverClaim, Set<UUID> members,
                         int outsiderPermissions) {
    }
}
