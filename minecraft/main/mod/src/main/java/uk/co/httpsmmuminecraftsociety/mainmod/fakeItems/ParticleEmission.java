package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.ParticleEmissionItemFeature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Emits particles only for equipped cosmetics and placed deco item frames. */
public final class ParticleEmission {
    private static final double VIEW_RADIUS = 32;
    private static final Map<Key, Schedule> SCHEDULES = new HashMap<>();

    private ParticleEmission() {}

    public static void tick(ServerLevel level) {
        long tick = level.getGameTime();
        Set<UUID> seenFrames = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive()) {
                FakeItem item = FakeItems.getFakeItemFromStack(player.getItemBySlot(EquipmentSlot.HEAD));
                if (item != null && item.getFeature(EquippableCosmeticItemFeature.class) != null) {
                    emit(level, player.getUUID(), item, tick, point -> wornPosition(player, point));
                }
            }
            for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class,
                    player.getBoundingBox().inflate(VIEW_RADIUS),
                    candidate -> !candidate.getItem().isEmpty())) {
                if (!seenFrames.add(frame.getUUID())) continue;
                FakeItem item = FakeItems.getFakeItemFromStack(frame.getItem());
                if (item != null) {
                    emit(level, frame.getUUID(), item, tick, point -> placedPosition(frame, point));
                }
            }
        }
        if (tick % 1200 == 0) {
            SCHEDULES.entrySet().removeIf(entry -> tick - entry.getValue().lastSeen > 1200);
        }
    }

    private static void emit(ServerLevel level, UUID sourceId, FakeItem item, long tick, Positioner positioner) {
        ParticleEmissionItemFeature feature = item.getFeature(ParticleEmissionItemFeature.class);
        if (feature == null) return;
        for (int index = 0; index < feature.particles().size(); index++) {
            var spec = feature.particles().get(index);
            Key key = new Key(level.dimension().identifier().toString(), sourceId, item.id(), index);
            Schedule schedule = SCHEDULES.computeIfAbsent(key,
                    ignored -> new Schedule(tick + delay(level, spec), tick));
            schedule.lastSeen = tick;
            if (tick < schedule.nextTick) continue;
            schedule.nextTick = tick + delay(level, spec);
            ParticleOptions options = spec.options(level.registryAccess());
            if (options == null) continue; // An unknown type or invalid particle arguments cannot be spawned.
            Vec3 modelPoint = new Vec3(
                    between(level, spec.from().x, spec.to().x),
                    between(level, spec.from().y, spec.to().y),
                    between(level, spec.from().z, spec.to().z));
            Vec3 worldPoint = positioner.at(modelPoint);
            // With a count of zero, the first speed argument selects note colour.
            double noteColor = options.getType() == ParticleTypes.NOTE ? level.getRandom().nextDouble() : 0;
            level.sendParticles(options, worldPoint.x, worldPoint.y, worldPoint.z,
                    0, noteColor, 0, 0, 1);
        }
    }

    private static int delay(ServerLevel level, ParticleEmissionItemFeature.Emission spec) {
        return spec.minTicks() + level.getRandom().nextInt(spec.maxTicks() - spec.minTicks() + 1);
    }

    private static double between(ServerLevel level, double low, double high) {
        return low + level.getRandom().nextDouble() * (high - low);
    }

    private static Vec3 wornPosition(ServerPlayer player, Vec3 point) {
        double x = (point.x - 8) * 1.5 / 16;
        double y = (point.y - 8) * 1.5 / 16;
        double z = (point.z - 8) * 1.5 / 16;
        double yaw = Math.toRadians(player.getYRot());
        return player.getEyePosition().add(x * Math.cos(yaw) - z * Math.sin(yaw), y + 0.25,
                x * Math.sin(yaw) + z * Math.cos(yaw));
    }

    private static Vec3 placedPosition(ItemFrame frame, Vec3 point) {
        double x = (point.x - 8) * 2 / 16;
        double y = (point.y - 8) * 2 / 16;
        double z = (point.z - 8) * 2 / 16;
        Direction direction = frame.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
        double angle = Math.toRadians(frame.getRotation() * 45.0);
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        if (direction == Direction.UP || direction == Direction.DOWN) {
            double rotatedX = x * cosine - z * sine;
            double rotatedZ = x * sine + z * cosine;
            return direction == Direction.UP
                    ? frame.position().add(rotatedX, y, rotatedZ)
                    : frame.position().add(rotatedX, -y, -rotatedZ);
        }
        double rotatedX = x * cosine - y * sine;
        double rotatedY = x * sine + y * cosine;
        Vec3 right = new Vec3(normal.z, 0, -normal.x);
        return frame.position().add(right.scale(rotatedX)).add(0, rotatedY, 0).add(normal.scale(z));
    }

    private record Key(String dimension, UUID source, String item, int index) {}
    private static final class Schedule {
        long nextTick;
        long lastSeen;
        Schedule(long nextTick, long lastSeen) {
            this.nextTick = nextTick;
            this.lastSeen = lastSeen;
        }
    }
    private interface Positioner { Vec3 at(Vec3 point); }
}
