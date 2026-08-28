package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

@Mixin(EnderEyeItem.class)
abstract class EnderEyePortalMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockDisabledEndPortal(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (FeatureToggles.isEnabled(FeatureToggles.END)
                || !context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.END_PORTAL_FRAME)) return;
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.literal("The End is currently disabled."), true);
        }
        callback.setReturnValue(InteractionResult.FAIL);
    }
}
