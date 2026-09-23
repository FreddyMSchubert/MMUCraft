package dev.freddy.killshift;

import net.minecraft.world.entity.Mob;

final class ShapeState {
    final MobForm form;
    Mob view;
    int landAir;
    int abilityCooldown;
    int angryTicks;
    boolean wasSneaking;

    ShapeState(MobForm form) {
        this.form = form;
        this.landAir = 300;
    }
}
