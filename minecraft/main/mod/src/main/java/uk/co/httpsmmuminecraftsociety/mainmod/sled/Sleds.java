package uk.co.httpsmmuminecraftsociety.mainmod.sled;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.DisplayEntityAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.ItemDisplayEntityAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Sleds {
    private static final String ITEM_ID = "sled";
    private static final String BOAT_TAG = "mainmod_sled";
    private static final String DISPLAY_TAG = "mainmod_sled_display";
    private static final String DISPLAY_CONFIGURED_TAG = "mainmod_sled_display_positioned";
    private static final String DISPLAY_OFFSET_TAG = "mainmod_sled_second_seat";
    private static final Set<Boat> BOATS = new HashSet<>();
    private static final Set<Display.ItemDisplay> DISPLAYS = new HashSet<>();
    private static final Map<UUID, Display.ItemDisplay> DETACHED = new HashMap<>();
    private static final Map<Display.ItemDisplay, Integer> ORPHAN_TICKS = new HashMap<>();
    private static final Map<UUID, MotionState> MOTION = new HashMap<>();
    private static final double WALK_SPEED = 0.215D;
    private static final double MAX_DOWNHILL_BONUS = 0.45D;
    private static final int MAX_MOMENTUM_TICKS = 100;

    private static final class MotionState {
        Vec3 lastPosition;
        int momentumTicks;
        int stepCooldown;
    }

    private Sleds() {}

    public static boolean isSled(Entity entity) {
        return entity instanceof Boat && entity.entityTags().contains(BOAT_TAG);
    }

    public static InteractionResult onUse(Level level, Player player, InteractionHand hand) {
        if (!FakeItems.isSpecificFakeItem(player.getItemInHand(hand), ITEM_ID)) return null;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return null;
        HitResult hit = player.pick(5.0D, 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK
                ? place(serverLevel, serverPlayer, hand, hit.getLocation()) : InteractionResult.PASS;
    }

    public static InteractionResult onUseOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !FakeItems.isSpecificFakeItem(player.getItemInHand(context.getHand()), ITEM_ID)) return null;
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return null;
        return place(level, serverPlayer, context.getHand(), context.getClickLocation());
    }

    private static InteractionResult place(ServerLevel level, ServerPlayer player, InteractionHand hand, Vec3 pos) {
        if (player.isPassenger()) return InteractionResult.FAIL;
        Boat boat = EntityTypes.POPLAR_BOAT.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (boat == null) return InteractionResult.FAIL;
        boat.setInitialPos(pos.x, pos.y, pos.z);
        boat.setYRot(player.getYRot());
        if (!level.noCollision(boat, boat.getBoundingBox())) return InteractionResult.FAIL;
        boat.addTag(BOAT_TAG);
        if (!level.addFreshEntity(boat)) return InteractionResult.FAIL;
        attachDisplay(level, boat);
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hit) {
        if (!(level instanceof ServerLevel) || !isSled(entity) || !(entity instanceof Boat boat))
            return InteractionResult.PASS;
        if (boat.getPassengers().stream().anyMatch(Player.class::isInstance)) return InteractionResult.FAIL;
        Display.ItemDisplay display = attachedDisplay(boat);
        if (display != null) {
            display.stopRiding();
            DETACHED.put(boat.getUUID(), display);
        }
        return InteractionResult.PASS;
    }

    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (isSled(entity)) BOATS.add((Boat) entity);
        if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(DISPLAY_TAG))
            DISPLAYS.add(display);
    }

    public static void onEntityUnload(Entity entity, ServerLevel level) {
        if (entity instanceof Display.ItemDisplay display) {
            DISPLAYS.remove(display);
            ORPHAN_TICKS.remove(display);
        }
        if (!isSled(entity)) return;
        Boat boat = (Boat) entity;
        BOATS.remove(boat);
        MOTION.remove(boat.getUUID());
        if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy())
            discardDisplay(boat);
        else DETACHED.remove(boat.getUUID());
    }

    public static void tick(ServerLevel level) {
        for (Display.ItemDisplay display : Set.copyOf(DISPLAYS)) {
            if (display.level() != level || display.isRemoved()) continue;
            if (isSled(display.getVehicle()) || DETACHED.containsValue(display)) {
                ORPHAN_TICKS.remove(display);
            } else if (ORPHAN_TICKS.merge(display, 1, Integer::sum) >= 5) {
                display.discard();
            }
        }
        for (Boat boat : BOATS) {
            if (boat.level() != level || boat.isRemoved()) continue;
            if (attachedDisplay(boat) == null) attachDisplay(level, boat);
            Display.ItemDisplay display = attachedDisplay(boat);
            if (display != null) {
                updateDisplayOffset(boat, display);
                display.setYRot(boat.getYRot());
            }

            MotionState state = MOTION.computeIfAbsent(boat.getUUID(), id -> new MotionState());
            Vec3 previous = state.lastPosition;
            state.lastPosition = boat.position();
            if (state.stepCooldown > 0) state.stepCooldown--;
            if (previous == null) continue;

            double drop = previous.y - boat.getY();
            if (drop > 0.04D) state.momentumTicks = Math.min(MAX_MOMENTUM_TICKS,
                    state.momentumTicks + Math.max(1, (int) Math.ceil(drop * 35.0D)));
            else state.momentumTicks = Math.max(0, state.momentumTicks - 1
                    - (int) Math.ceil(Math.max(0.0D, -drop) * 35.0D));

            if (!(boat.getControllingPassenger() instanceof ServerPlayer rider)
                    || !boat.getPaddleState(0) || !boat.getPaddleState(1)) continue;

            Vec3 movement = boat.position().subtract(previous);
            Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
            double yaw = Math.toRadians(boat.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            stepUp(level, boat, rider, forward, state);
            if (!onSnow(level, boat)) continue;

            double speed = WALK_SPEED + MAX_DOWNHILL_BONUS * state.momentumTicks / MAX_MOMENTUM_TICKS;
            double friction = boat.getGroundFriction();
            if (!Double.isFinite(friction) || friction < 0.3D) friction = 0.6D;
            double packetSpeed = Math.min(0.9D, (speed - 0.04D) / friction);
            Vec3 velocity = horizontal.scale(0.25D).add(forward.scale(packetSpeed * 0.75D));
            if (Math.abs(horizontal.length() - speed) > 0.02D || horizontal.normalize().dot(forward) < 0.98D) {
                boat.setDeltaMovement(velocity.x, boat.getDeltaMovement().y, velocity.z);
                rider.connection.send(new ClientboundSetEntityMotionPacket(boat));
            }
        }
    }

    private static boolean onSnow(ServerLevel level, Boat boat) {
        AABB box = boat.getBoundingBox();
        double[] xs = {boat.getX(), box.minX + 0.1D, box.maxX - 0.1D};
        double[] zs = {boat.getZ(), box.minZ + 0.1D, box.maxZ - 0.1D};
        for (double x : xs) for (double z : zs) {
            BlockPos below = BlockPos.containing(x, box.minY - 0.05D, z);
            if (snowy(level.getBlockState(below)) || snowy(level.getBlockState(below.above()))) return true;
        }
        return false;
    }

    private static boolean snowy(BlockState state) {
        return state.is(BlockTags.SNOW) || state.hasProperty(BlockStateProperties.SNOWY)
                && state.getValue(BlockStateProperties.SNOWY);
    }

    private static void stepUp(ServerLevel level, Boat boat, ServerPlayer rider, Vec3 forward, MotionState state) {
        if (state.stepCooldown > 0) return;
        AABB box = boat.getBoundingBox();
        Vec3 probe = forward.scale(0.35D);
        if (level.noBlockCollision(boat, box.move(probe))) return;
        for (int tenth = 1; tenth <= 11; tenth++) {
            double height = tenth / 10.0D;
            if (!level.noCollision(boat, box.move(0.0D, height, 0.0D))
                    || !level.noCollision(boat, box.move(probe.x, height, probe.z))) continue;
            double oldY = boat.getY();
            boat.move(MoverType.SELF, new Vec3(0.0D, height, 0.0D));
            if (boat.getY() > oldY + height - 0.02D) {
                state.lastPosition = boat.position();
                state.momentumTicks = Math.max(0, state.momentumTicks - (int) Math.ceil(height * 35.0D));
                state.stepCooldown = 2;
                rider.connection.send(ClientboundMoveVehiclePacket.fromEntity(boat));
            }
            return;
        }
    }

    private static Display.ItemDisplay attachedDisplay(Boat boat) {
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Display.ItemDisplay display && display.entityTags().contains(DISPLAY_TAG))
                return display;
        }
        return null;
    }

    private static void attachDisplay(ServerLevel level, Boat boat) {
        Display.ItemDisplay display = DETACHED.remove(boat.getUUID());
        if (display == null || display.isRemoved()) {
            display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
            display.addTag(DISPLAY_TAG);
            display.setNoGravity(true);
            display.setSilent(true);
            display.setPermanentlyInvulnerable(true);
            display.setPosRotInterpolationDuration(2);
            display.setPos(boat.position());
            ((ItemDisplayEntityAccessor) display).mainmod$setItemStack(FakeItems.createFakeItemStack(ITEM_ID, 1));
            ((ItemDisplayEntityAccessor) display).mainmod$setItemTransform(ItemDisplayContext.FIXED);
            ((DisplayEntityAccessor) display).mainmod$setShadowRadius(0.0F);
            ((DisplayEntityAccessor) display).mainmod$setShadowStrength(0.0F);
            if (!level.addFreshEntity(display)) return;
        }
        if (!display.startRiding(boat, false, false)) display.discard();
        else {
            boat.positionRider(display);
            updateDisplayOffset(boat, display);
        }
    }

    private static void updateDisplayOffset(Boat boat, Display.ItemDisplay display) {
        boolean secondSeat = boat.getPassengers().indexOf(display) == 1;
        if (display.entityTags().contains(DISPLAY_CONFIGURED_TAG)
                && secondSeat == display.entityTags().contains(DISPLAY_OFFSET_TAG)) return;
        display.addTag(DISPLAY_CONFIGURED_TAG);
        if (secondSeat) display.addTag(DISPLAY_OFFSET_TAG);
        else display.removeTag(DISPLAY_OFFSET_TAG);
        ((DisplayEntityAccessor) display).mainmod$setTransformation(new Transformation(
                new Vector3f(0.0F, 0.5F, secondSeat ? 0.6F : 0.0F), new Quaternionf(),
                new Vector3f(1.45F, 1.0F, 1.45F), new Quaternionf()));
    }

    public static void discardDisplay(Boat boat) {
        Display.ItemDisplay display = attachedDisplay(boat);
        if (display != null) display.discard();
        display = DETACHED.remove(boat.getUUID());
        if (display != null) display.discard();
    }

    public static void destroy(Boat boat, ServerLevel level) {
        discardDisplay(boat);
        boat.kill(level);
        if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS))
            boat.spawnAtLocation(level, FakeItems.createFakeItemStack(ITEM_ID, 1));
    }

    public static void clear() {
        BOATS.clear();
        DISPLAYS.clear();
        DETACHED.clear();
        ORPHAN_TICKS.clear();
        MOTION.clear();
    }
}
