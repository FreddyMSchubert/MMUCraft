package dev.freddy.killshift.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobFlagsAccessor {
    @Accessor("DATA_MOB_FLAGS_ID")
    static EntityDataAccessor<Byte> killshift$mobFlags() {
        throw new AssertionError();
    }
}
