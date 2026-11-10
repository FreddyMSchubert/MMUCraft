package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.BunnyPajamasCharm;

@Mixin(LivingEntity.class)
public abstract class BunnyPajamasJumpMixin {
    @ModifyExpressionValue(method = "getJumpPower(F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double mainmod$normalJumpWhileSneaking(double jumpStrength) {
        if (!((Object) this instanceof Player player) || !player.isShiftKeyDown()) return jumpStrength;

        int level = CharmsManager.getPlayerCharmLevel(player, BunnyPajamasCharm.class);
        return jumpStrength - BunnyPajamasCharm.getJumpStrengthAdditionPerLevel(level);
    }
}
