package dev.freddy.killshift;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class GiantSpawns {
    public static final String SPAWN_TAG = "killshift_spawn_giant";

    private GiantSpawns() {
    }

    static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Zombie zombie) || !zombie.removeTag(SPAWN_TAG)) return;
        zombie.convertTo(EntityTypes.GIANT, ConversionParams.single(zombie, true, true),
                EntitySpawnReason.CONVERSION, giant -> {
                    giant.finalizeSpawn(level, level.getCurrentDifficultyAt(giant.blockPosition()),
                            EntitySpawnReason.CONVERSION, null);
                    giant.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(35.0);
                    giant.setHealth(giant.getMaxHealth());
                });
    }
}
