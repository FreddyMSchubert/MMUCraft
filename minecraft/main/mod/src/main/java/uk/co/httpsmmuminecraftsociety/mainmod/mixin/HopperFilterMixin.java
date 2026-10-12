package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.HopperFilter;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHopper;

@Mixin(HopperBlockEntity.class)
public abstract class HopperFilterMixin implements WorldlyContainer {
    private static final int[] MAINMOD$SLOTS = {0, 1, 2, 3, 4};
    private static final ThreadLocal<Hopper> MAINMOD$SUCKING_HOPPER = new ThreadLocal<>();

    @Shadow private static int[] getSlots(Container container, Direction direction) { throw new AssertionError(); }
    @Shadow private static boolean tryTakeInItemFromSlot(Hopper hopper, Container source, int slot,
                                                          Direction direction) { throw new AssertionError(); }

    @WrapMethod(method = "suckInItems")
    private static boolean mainmod$trackSuction(Level level, Hopper hopper, Operation<Boolean> original) {
        MAINMOD$SUCKING_HOPPER.set(hopper);
        try {
            if (hopper instanceof HopperBlockEntity block
                    && ((UpsideDownHopper) block).mainmod$isUpsideDown()) {
                return mainmod$suckFromBelow(level, hopper);
            }
            return original.call(level, hopper);
        } finally {
            MAINMOD$SUCKING_HOPPER.remove();
        }
    }

    @Unique
    private static boolean mainmod$suckFromBelow(Level level, Hopper hopper) {
        BlockPos below = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() - 1.0,
                hopper.getLevelZ());
        Container source = HopperBlockEntity.getContainerAt(level, below);
        if (source != null) {
            for (int slot : getSlots(source, Direction.UP)) {
                if (tryTakeInItemFromSlot(hopper, source, slot, Direction.UP)) return true;
            }
            return false;
        }

        BlockState state = level.getBlockState(below);
        if (state.isCollisionShapeFullBlock(level, below) && !state.is(BlockTags.DOES_NOT_BLOCK_HOPPERS)) {
            return false;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(below),
                EntitySelector.ENTITY_STILL_ALIVE)) {
            if (HopperBlockEntity.addItem(hopper, item)) return true;
        }
        return false;
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
