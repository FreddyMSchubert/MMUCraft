package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.HopperFilter;

@Mixin(HopperBlockEntity.class)
public abstract class HopperFilterMixin {
    @Redirect(
            method = "ejectItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private static ItemStack mainmod$hideFilterFromEjection(HopperBlockEntity hopper, int slot) {
        ItemStack stack = hopper.getItem(slot);
        return HopperFilter.isFilter(stack) ? ItemStack.EMPTY : stack;
    }

    @Inject(method = "tryTakeInItemFromSlot", at = @At("HEAD"), cancellable = true)
    private static void mainmod$filterContainerSuction(Hopper hopper, Container source, int slot,
                                                       Direction direction, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = source.getItem(slot);
        if (HopperFilter.isFilter(stack) || !HopperFilter.allows(hopper, stack)) cir.setReturnValue(false);
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void mainmod$filterLooseItemSuction(Container hopper, ItemEntity entity,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (HopperFilter.isFilter(entity.getItem()) || !HopperFilter.allows(hopper, entity.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
