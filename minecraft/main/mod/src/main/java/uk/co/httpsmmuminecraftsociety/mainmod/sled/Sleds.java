package uk.co.httpsmmuminecraftsociety.mainmod.sled;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
    private static final Map<UUID, Display.ItemDisplay> DETACHED = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final double FORWARD_BUMP = 0.5D;
    private static final double MAX_SPEED = 1.2D;

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
        if (!player.startRiding(boat)) {
            boat.discard();
            return InteractionResult.FAIL;
        }
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
    }

    public static void onEntityUnload(Entity entity, ServerLevel level) {
        if (!isSled(entity)) return;
        Boat boat = (Boat) entity;
        BOATS.remove(boat);
        LAST_POSITION.remove(boat.getUUID());
        if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy())
            discardDisplay(boat);
        else DETACHED.remove(boat.getUUID());
    }

    public static void tick(ServerLevel level) {
        for (Boat boat : BOATS) {
            if (boat.level() != level || boat.isRemoved()) continue;
            if (attachedDisplay(boat) == null) attachDisplay(level, boat);
            Display.ItemDisplay display = attachedDisplay(boat);
            if (display != null) {
                updateDisplayOffset(boat, display);
                display.setYRot(boat.getYRot());
            }

            Vec3 previous = LAST_POSITION.put(boat.getUUID(), boat.position());
            if (!(boat.getControllingPassenger() instanceof ServerPlayer rider) || previous == null || !onSnow(level, boat)
                    || !boat.getPaddleState(0) || !boat.getPaddleState(1))
                continue;

            Vec3 movement = boat.position().subtract(previous);
            Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
            double yaw = Math.toRadians(boat.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            Vec3 boost = horizontal.add(forward.scale(FORWARD_BUMP));
            if (boost.lengthSqr() > MAX_SPEED * MAX_SPEED) boost = boost.normalize().scale(MAX_SPEED);
            boat.setDeltaMovement(boost.x, boat.getDeltaMovement().y, boost.z);
            rider.connection.send(new ClientboundSetEntityMotionPacket(boat));
        }
    }

    private static boolean onSnow(ServerLevel level, Boat boat) {
        BlockPos below = BlockPos.containing(boat.getX(), boat.getBoundingBox().minY - 0.05D, boat.getZ());
        var state = level.getBlockState(below);
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
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
        DETACHED.clear();
        LAST_POSITION.clear();
    }
}
