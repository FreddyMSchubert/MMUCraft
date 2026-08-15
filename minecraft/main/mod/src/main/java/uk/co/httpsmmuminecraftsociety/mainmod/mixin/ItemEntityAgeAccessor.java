package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAgeAccessor {
    @Accessor("age")
    void mainmod$setAge(int age);
}
