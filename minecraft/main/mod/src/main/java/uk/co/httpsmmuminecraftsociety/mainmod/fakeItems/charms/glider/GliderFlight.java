package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class GliderFlight {
    public static final double GLIDER_SPEED_BPS = 17.0;
    public static final double ELYTRA_SPEED_BPS = 20.0;
    public static final double BOOST_SPEED_BPS = 20.0;
    public static final double SPEED_DECAY_BPS_PER_TICK = 0.02;
    public static final double UPWARD_DAMPING = 0.95;
    public static final int ASCENT_GRACE_TICKS = 50;

    private static final Map<ServerPlayer, FlightState> STATES = new WeakHashMap<>();

    private static final class FlightState {
        double speedLimit = GLIDER_SPEED_BPS;
        int ascentGraceUntil;
        int impulseTick = Integer.MIN_VALUE;
        Vec3 previousPosition;
        ResourceKey<Level> dimension;
        Updrafts.Updraft updraft;
    }

    public static void init() {
        EnchantmentEvents.ALLOW_ENCHANTING.register(GliderCharm::allowEnchanting);
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(GliderFlight::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    public static void allowImpulseAscent(ServerPlayer player) {
        if (!GliderCharm.isGlider(player.getItemBySlot(EquipmentSlot.CHEST))) return;
        FlightState state = STATES.computeIfAbsent(player, ignored -> new FlightState());
        state.ascentGraceUntil = player.tickCount + ASCENT_GRACE_TICKS;
        state.impulseTick = player.tickCount;
    }

    public static boolean touchesFluid(LivingEntity entity) {
        var box = entity.getBoundingBox().deflate(0.001);
        var level = entity.level();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && pos.getY() + fluid.getHeight(level, pos) > box.minY) return true;
        }
        return false;
    }

    private static void tick(ServerPlayer player) {
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean glider = GliderCharm.isGlider(chest);
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying
                || !chest.is(Items.ELYTRA) || !chest.has(DataComponents.GLIDER)) {
            STATES.remove(player);
            return;
        }

        FlightState state = STATES.computeIfAbsent(player, ignored -> new FlightState());
        Vec3 position = player.getBoundingBox().getCenter();
        if (!glider) state.updraft = null;
        if (state.dimension != player.level().dimension()) {
            state.updraft = null;
            state.speedLimit = GLIDER_SPEED_BPS;
            state.previousPosition = null;
            state.dimension = player.level().dimension();
        }
        if (!player.isFallFlying()) {
            state.updraft = null;
            state.speedLimit = GLIDER_SPEED_BPS;
            state.previousPosition = position;
            if (player.tickCount >= state.ascentGraceUntil) STATES.remove(player);
            return;
        }
        if (glider && touchesFluid(player)) {
            player.stopFallFlying();
            STATES.remove(player);
            return;
        }

        // Use the server velocity for two ticks after an impulse.
        Vec3 velocity = player.tickCount <= state.impulseTick + 1
                ? player.getDeltaMovement() : player.getKnownMovement();
        Vec3 previous = state.previousPosition == null ? position.subtract(velocity) : state.previousPosition;
        state.previousPosition = position;
        state.speedLimit = decaySpeedLimit(state.speedLimit);

        Vec3 original = velocity;
        Vec3 frameNormal = BoostFrames.crossedFrame(player.level(), previous, position);
        if (frameNormal != null) {
            double extra = BOOST_SPEED_BPS / 20.0 - velocity.dot(frameNormal);
            if (extra > 0) velocity = velocity.add(frameNormal.scale(extra));
            state.speedLimit = BOOST_SPEED_BPS;
            state.ascentGraceUntil = player.tickCount + ASCENT_GRACE_TICKS;
        }

        if (glider) {
            Updrafts.Updraft caught = Updrafts.findAt(player);
            if (caught != null) state.updraft = caught;
            double lift = state.updraft == null ? 0 : state.updraft.liftAt(player.getBoundingBox().minY, player.tickCount);
            if (lift > 0) {
                velocity = new Vec3(velocity.x, Math.min(Updrafts.MAX_UPWARD_SPEED, velocity.y + lift), velocity.z);
                state.speedLimit = BOOST_SPEED_BPS;
                state.ascentGraceUntil = player.tickCount + ASCENT_GRACE_TICKS;
            } else {
                state.updraft = null;
            }
            if (player.tickCount >= state.ascentGraceUntil && velocity.y > 0) {
                velocity = new Vec3(velocity.x, velocity.y * UPWARD_DAMPING, velocity.z);
            }
        }

        velocity = clampSpeed(velocity, glider ? state.speedLimit : ELYTRA_SPEED_BPS);
        if (velocity.distanceToSqr(original) > 1.0E-10) {
            player.setDeltaMovement(velocity);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    public static double decaySpeedLimit(double speedLimit) {
        return Math.max(GLIDER_SPEED_BPS, speedLimit - SPEED_DECAY_BPS_PER_TICK);
    }

    public static Vec3 clampSpeed(Vec3 velocity, double speedBps) {
        double limit = speedBps / 20.0;
        return velocity.lengthSqr() > limit * limit ? velocity.normalize().scale(limit) : velocity;
    }
}
