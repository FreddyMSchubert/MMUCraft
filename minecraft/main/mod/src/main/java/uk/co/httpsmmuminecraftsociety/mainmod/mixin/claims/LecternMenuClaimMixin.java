package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.LecternClaimMenu;

@Mixin(LecternMenu.class)
abstract class LecternMenuClaimMixin implements LecternClaimMenu {
    @Unique private LecternBlockEntity mainmod$lectern;

    @Override
    public void mainmod$setLectern(LecternBlockEntity lectern) {
        mainmod$lectern = lectern;
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void mainmod$guardTakeBook(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (!player.level().isClientSide() && buttonId == LecternMenu.BUTTON_TAKE_BOOK
                && (mainmod$lectern == null || !ClaimsManager.checkDirect(player, mainmod$lectern.getLevel(),
                        mainmod$lectern.getBlockPos(), ClaimsManager.BREAK))) {
            cir.setReturnValue(false);
        }
    }
}
