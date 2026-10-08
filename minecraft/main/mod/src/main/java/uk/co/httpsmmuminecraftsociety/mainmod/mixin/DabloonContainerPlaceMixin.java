package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(BlockItem.class)
public abstract class DabloonContainerPlaceMixin {
    @Inject(method = "place", at = @At("HEAD"))
    private void mainmod$beforePlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir,
                                     @Share("currency") LocalRef<ItemStack> currency) {
        if (context.getPlayer() instanceof ServerPlayer
                && DabloonInteractionLog.kind(context.getItemInHand()).equals("container")) {
            currency.set(context.getItemInHand().copy());
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void mainmod$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir,
                                    @Share("currency") LocalRef<ItemStack> currency) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || currency.get() == null
                || !cir.getReturnValue().consumesAction()) return;
        ItemStack placed = currency.get().copyWithCount(1);
        DabloonInteractionLog.record(player, "transfer", "container", DabloonInteractionLog.itemId(placed),
                1, DabloonInteractionLog.value(placed), null, null,
                "Placed at " + context.getClickedPos().toShortString() + "; contents: "
                        + DabloonInteractionLog.describe(placed));
    }
}
