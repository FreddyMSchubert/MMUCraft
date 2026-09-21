package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootOpenDelegateView;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.entity.BarrelBlockEntity$1")
public abstract class BarrelOpenersCounterMixin {
    @Shadow(remap = false)
    @Final
    private BarrelBlockEntity this$0;

    @Inject(method = "isOwnContainer", at = @At("HEAD"), cancellable = true)
    private void pil$isInstancedLootContainer(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player.containerMenu instanceof ChestMenu menu) {
            Container container = menu.getContainer();
            if (container instanceof InstancedLootOpenDelegateView view && view.pil$hasOpenDelegate(this.this$0)) {
                cir.setReturnValue(true);
            }
        }
    }
}
