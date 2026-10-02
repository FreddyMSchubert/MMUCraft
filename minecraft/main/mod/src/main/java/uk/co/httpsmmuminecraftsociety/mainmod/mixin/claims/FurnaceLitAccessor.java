package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceLitAccessor {
    @Accessor("litTimeRemaining")
    int mainmod$getLitTimeRemaining();
}
