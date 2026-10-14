package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.ThickSocksCharm;

@Mixin(PowderSnowBlock.class)
public abstract class ThickSocksPowderSnowMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void mainmod$walkOnSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && ThickSocksCharm.isActive(player.getItemBySlot(EquipmentSlot.FEET))) {
            cir.setReturnValue(true);
        }
    }
}
