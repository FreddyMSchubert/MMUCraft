package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayEntityAccessor {
    @Invoker("setItemStack")
    void mainmod$setItemStack(ItemStack stack);

    @Invoker("setItemTransform")
    void mainmod$setItemTransform(ItemDisplayContext context);
}
