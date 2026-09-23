package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;

@Mixin(Player.class)
public abstract class ReskinnedHelmetTick {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$keepHelmetProtection(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (player.level().isClientSide() || head.isEmpty()) return;

        CosmeticsManager.restoreHelmetProperties(head);
        if (CosmeticsManager.isReskinnedTurtleHelmet(head) && !player.isEyeInFluid(FluidTags.WATER)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true));
        }
    }
}
