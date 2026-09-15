package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.HopperFilter;

@Mixin(HopperBlockEntity.class)
public abstract class HopperFilterMixin implements WorldlyContainer {
    private static final int[] MAINMOD$SLOTS = {0, 1, 2, 3, 4};
    private static final ThreadLocal<Hopper> MAINMOD$SUCKING_HOPPER = new ThreadLocal<>();

    @WrapMethod(method = "suckInItems")
    private static boolean mainmod$trackSuction(Level level, Hopper hopper, Operation<Boolean> original) {
        MAINMOD$SUCKING_HOPPER.set(hopper);
        try {
            return original.call(level, hopper);
        } finally {
            MAINMOD$SUCKING_HOPPER.remove();
        }
    }

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

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return MAINMOD$SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return !HopperFilter.isFilter(stack)
                && (MAINMOD$SUCKING_HOPPER.get() != this
                || HopperFilter.allows((Container) (Object) this, stack));
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return !HopperFilter.isFilter(stack);
    }
}
