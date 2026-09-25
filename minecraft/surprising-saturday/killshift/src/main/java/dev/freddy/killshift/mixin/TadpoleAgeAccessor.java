package dev.freddy.killshift.mixin;

import net.minecraft.world.entity.animal.frog.Tadpole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Tadpole.class)
public interface TadpoleAgeAccessor {
    @Invoker("setAgeLocked")
    void killshift$setAgeLocked(boolean locked);
}
