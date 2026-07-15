package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private void mainmod$acceptFourLeafClover(ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (FakeItems.isSpecificFakeItem(ingredient, "4-leaf-clover")) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasPotionMix", at = @At("HEAD"), cancellable = true)
    private void mainmod$recognizeLuckMix(ItemStack potion, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (mainmod$isLuckMix(potion, ingredient)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void mainmod$brewLuck(ItemStack ingredient, ItemStack potion, CallbackInfoReturnable<ItemStack> cir) {
        if (mainmod$isLuckMix(potion, ingredient)) {
            cir.setReturnValue(PotionContents.createItemStack(potion.getItem(), Potions.LUCK));
        }
    }

    private static boolean mainmod$isLuckMix(ItemStack potion, ItemStack ingredient) {
        return FakeItems.isSpecificFakeItem(ingredient, "4-leaf-clover")
                && potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD);
    }
}
