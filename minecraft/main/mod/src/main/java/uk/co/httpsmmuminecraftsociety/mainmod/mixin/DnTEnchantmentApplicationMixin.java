package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.DisabledDnTEnchantments;

/** Covers direct enchantment application, including anvils and commands. */
@Mixin(ItemEnchantments.Mutable.class)
public abstract class DnTEnchantmentApplicationMixin {
    @Inject(method = "set", at = @At("HEAD"), cancellable = true, require = 1)
    private void mainmod$rejectDnTEnchantment(Holder<Enchantment> enchantment, int level, CallbackInfo ci) {
        if (DisabledDnTEnchantments.contains(enchantment)) {
            ci.cancel();
        }
    }
}
