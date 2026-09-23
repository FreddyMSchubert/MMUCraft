package dev.freddy.killshift;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

record MobForm(
        EntityType<? extends Mob> type,
        MobTraits traits,
        float maxHealth,
        double attackDamage,
        double armor,
        double scale
) {
}
