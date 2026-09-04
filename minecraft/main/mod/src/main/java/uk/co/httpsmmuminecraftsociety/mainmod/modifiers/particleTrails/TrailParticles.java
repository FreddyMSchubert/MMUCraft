package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

public final class TrailParticles {
    private TrailParticles() {}

    public static Vec3 behind(Vec3 position, Vec3 movement, double fraction, boolean elytra) {
        Vec3 offset = movement.scale(fraction);
        if (elytra) offset = offset.add(movement.normalize().scale(2.0));
        return position.subtract(offset);
    }

    public static void spawn(Entity source, WeightedTrailSpec spec, ServerPlayer player, boolean elytra) {
        if (!(source.level() instanceof ServerLevel level)) return;
        boolean member = PlayerStatsSync.isMember(player);
        if (spec.totalWeight(member) == 0 || source.tickCount % spec.interval(member) != 0) return;
        Vec3 movement = source.getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-6) return;

        for (int index = 0; index < 2; index++) {
            TrailParticle particle = spec.pick(source.getRandom(), member);
            if (particle == null) continue;
            Vec3 position = behind(source.position(), movement, (index + 0.5) / 2, elytra);
            // With count zero, note particles use the X value as their colour.
            double note = particle == TrailParticle.NOTE ? source.getRandom().nextDouble() : 0;
            if (elytra) {
                for (ServerPlayer viewer : level.players()) {
                    if (viewer == player) continue;
                    level.sendParticles(viewer, particle.options, false, false,
                            position.x, position.y, position.z, 0, note, 0, 0, 1);
                }
            } else {
                level.sendParticles(particle.options, position.x, position.y, position.z, 0, note, 0, 0, 1);
            }
        }
    }
}
