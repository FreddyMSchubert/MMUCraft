package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

final class CharmParticles {
    private CharmParticles() {}

    static void atBlock(ServerLevel level, BlockPos pos, ParticleOptions particle, int count) {
        RandomSource random = level.getRandom();
        level.sendParticles(particle,
                pos.getX() + 0.3 + random.nextDouble() * 0.4,
                pos.getY() + 0.3 + random.nextDouble() * 0.4,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                count, 0.2, 0.2, 0.2, 0.01);
    }
}
