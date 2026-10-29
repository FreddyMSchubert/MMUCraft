package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternBlockEntity.class)
abstract class LecternBlockEntityClaimMixin {
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void mainmod$bindLectern(int containerId, Inventory inventory, Player player,
                                            CallbackInfoReturnable<AbstractContainerMenu> cir) {
        ((LecternClaimMenu) cir.getReturnValue()).mainmod$setLectern((LecternBlockEntity) (Object) this);
    }
}
