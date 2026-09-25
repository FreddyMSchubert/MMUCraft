package dev.freddy.killshift.mixin;

import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Guardian.class)
public interface GuardianAttackAccessor {
    @Invoker("setActiveAttackTarget")
    void killshift$setActiveAttackTarget(int entityId);
}
