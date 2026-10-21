package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(ItemEntity.class)
public abstract class DabloonItemPickupMixin {
    @Unique private ItemStack mainmod$pickupStack = ItemStack.EMPTY;
    @Unique private int mainmod$balanceBefore;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void mainmod$beforePickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
                && !ClaimsManager.canPerform(serverPlayer, serverPlayer.level(),
                        ((ItemEntity) (Object) this).blockPosition(), ClaimsManager.ITEM_PICKUP)) {
            ci.cancel();
            return;
        }
        ItemStack stack = ((ItemEntity)(Object)this).getItem();
        if (player instanceof ServerPlayer serverPlayer && !DabloonInteractionLog.kind(stack).isEmpty()) {
            mainmod$pickupStack = stack.copy();
            mainmod$balanceBefore = MoneyHelper.GetBalance(serverPlayer);
        }
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void mainmod$afterPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || mainmod$pickupStack.isEmpty()) return;
        ItemEntity entity = (ItemEntity)(Object)this;
        int taken = mainmod$pickupStack.getCount() - (entity.isRemoved() ? 0 : entity.getItem().getCount());
        if (taken > 0) {
            ItemStack picked = mainmod$pickupStack.copyWithCount(taken);
            DabloonInteractionLog.record(serverPlayer, "pickup", DabloonInteractionLog.kind(picked),
                    DabloonInteractionLog.itemId(picked), taken, DabloonInteractionLog.value(picked),
                    mainmod$balanceBefore, MoneyHelper.GetBalance(serverPlayer),
                    "Item entity " + entity.getUUID() + "; contents: " + DabloonInteractionLog.describe(picked));
        }
        mainmod$pickupStack = ItemStack.EMPTY;
    }
}
