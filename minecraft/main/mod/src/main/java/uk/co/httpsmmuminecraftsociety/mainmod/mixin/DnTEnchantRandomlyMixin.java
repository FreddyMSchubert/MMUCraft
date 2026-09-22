package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.DisabledDnTEnchantments;

import java.util.Optional;
import java.util.stream.Stream;

/** Filters both structure loot and DnT's data-driven librarian offers. */
@Mixin(EnchantRandomlyFunction.class)
public abstract class DnTEnchantRandomlyMixin {
    @Shadow
    private Optional<HolderSet<Enchantment>> options;

    @ModifyExpressionValue(
            method = "run",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
            require = 1
    )
    private Stream<Holder<Enchantment>> mainmod$excludeDnTEnchantments(
            Stream<Holder<Enchantment>> candidates
    ) {
        return candidates.filter(enchantment -> !DisabledDnTEnchantments.contains(enchantment));
    }

    @Inject(method = "run", at = @At("RETURN"), cancellable = true, require = 1)
    private void mainmod$discardForbiddenOnlyResult(
            ItemStack input,
            LootContext context,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (options.isEmpty() || options.get().size() == 0
                || !options.get().stream().allMatch(DisabledDnTEnchantments::contains)) {
            return;
        }

        ItemStack result = callback.getReturnValue();
        ItemEnchantments stored = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.isEmpty()) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
