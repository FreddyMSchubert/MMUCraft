package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(HopperBlockEntity.class)
public abstract class DabloonHopperTransferMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private static void mainmod$beforeTransfer(@Nullable Container from, Container into, ItemStack stack,
                                                @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir,
                                                @Share("currency") LocalRef<ItemStack> original) {
        if (!DabloonInteractionLog.kind(stack).isEmpty()) original.set(stack.copy());
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private static void mainmod$afterTransfer(@Nullable Container from, Container into, ItemStack stack,
                                               @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir,
                                               @Share("currency") LocalRef<ItemStack> original) {
        ItemStack before = original.get();
        if (before == null) return;
        int moved = before.getCount() - cir.getReturnValue().getCount();
        if (moved <= 0) return;
        ItemStack transferred = before.copyWithCount(moved);
        Container locationContainer = into instanceof BlockEntity || into instanceof Entity ? into : from;
        String dimension = "unknown";
        int x = 0, y = 0, z = 0;
        if (locationContainer instanceof BlockEntity block && block.getLevel() != null) {
            dimension = block.getLevel().dimension().identifier().toString();
            x = block.getBlockPos().getX();
            y = block.getBlockPos().getY();
            z = block.getBlockPos().getZ();
        } else if (locationContainer instanceof Entity entity) {
            dimension = entity.level().dimension().identifier().toString();
            x = entity.blockPosition().getX();
            y = entity.blockPosition().getY();
            z = entity.blockPosition().getZ();
        }
        DabloonInteractionLog.recordWorld("transfer", DabloonInteractionLog.kind(transferred),
                DabloonInteractionLog.itemId(transferred), moved, DabloonInteractionLog.value(transferred),
                dimension, x, y, z, "Automation " + mainmod$container(from)
                        + " -> " + mainmod$container(into) + "; direction=" + direction
                        + "; contents: " + DabloonInteractionLog.describe(transferred));
    }

    @Unique
    private static String mainmod$container(@Nullable Container container) {
        if (container == null) return "item entity or direct insertion";
        if (container instanceof BlockEntity block) {
            return container.getClass().getSimpleName() + " at " + block.getBlockPos().toShortString();
        }
        if (container instanceof Entity entity) {
            return container.getClass().getSimpleName() + " " + entity.getUUID()
                    + " at " + entity.blockPosition().toShortString();
        }
        return container.getClass().getSimpleName();
    }
}
