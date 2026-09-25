package dev.freddy.killshift.mixin;

import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AgeableMob.class)
public interface AgeableMobAgeAccessor {
    @Invoker("setAgeLocked")
    void killshift$setAgeLocked(boolean locked);
}
