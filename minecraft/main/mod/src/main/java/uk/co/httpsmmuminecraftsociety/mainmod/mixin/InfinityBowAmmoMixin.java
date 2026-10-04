package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class InfinityBowAmmoMixin {
    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void mainmod$defaultArrowForInfinity(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty() || !weapon.is(Items.BOW)) return;

        Player player = (Player) (Object) this;
        var infinity = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY);
        if (EnchantmentHelper.getItemEnchantmentLevel(infinity, weapon) > 0) {
            cir.setReturnValue(new ItemStack(Items.ARROW));
        }
    }
}
