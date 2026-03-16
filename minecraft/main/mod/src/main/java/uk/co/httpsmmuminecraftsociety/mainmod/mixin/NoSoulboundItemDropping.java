package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;

@Mixin(Inventory.class)
public abstract class NoSoulboundItemDropping {
    @Shadow @Final public Player player;

    @Shadow
    public abstract int getContainerSize();

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Shadow
    public abstract void setItem(int slot, ItemStack stack);

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void mainmod$keepSoulboundItems(CallbackInfo ci) {
        Holder<Enchantment> soulbound = this.player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SOULBOUND);

        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack stack = this.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack) > 0) {
                continue;
            }

            this.player.drop(stack, true, false);
            this.setItem(slot, ItemStack.EMPTY);
        }

        ci.cancel();
    }
}
