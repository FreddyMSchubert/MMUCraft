package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.WingedShoesCharm;

@Mixin(MaceItem.class)
public class WingedShoesMaceSmashMixin {
    @Inject(method = "hurtEnemy", at = @At("TAIL"))
    private void mainmod$resetJumpsOnSmash(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
        if (attacker instanceof ServerPlayer player && MaceItem.canSmashAttack(player)) {
            WingedShoesCharm.resetJumps(player);
        }
    }
}
