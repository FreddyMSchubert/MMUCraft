package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class SoulboundItemBreakAdvancement {
    @Inject(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void mainmod$grantSoulboundBreak(int damage, ServerPlayer player, Consumer<ItemStack> breakCallback, CallbackInfo ci) {
        if (player == null) return;
        ItemStack stack = (ItemStack) (Object) this;
        var soulbound = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SOULBOUND);
        if (EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack) > 0) {
            MasteryAdvancements.grant(player, "utility/soulbound_break");
        }
    }
}
