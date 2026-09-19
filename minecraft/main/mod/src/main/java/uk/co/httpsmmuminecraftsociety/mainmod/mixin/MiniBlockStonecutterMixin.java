package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

@Mixin(StonecutterMenu.class)
abstract class MiniBlockStonecutterMixin {
    @Shadow @Final private ResultContainer resultContainer;

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void mainmod$gateMiniBlockResult(int index, CallbackInfo callback) {
        if (FeatureToggles.isEnabled(FeatureToggles.WELCOMING)) return;

        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        if (index < 0 || index >= menu.getNumberOfVisibleRecipes()) return;
        boolean miniBlock = menu.getVisibleRecipes().entries().get(index).recipe().recipe()
                .map(holder -> holder.id().identifier())
                .filter(id -> id.getNamespace().equals("mainmod") && id.getPath().startsWith("miniblocks/"))
                .isPresent();
        if (miniBlock) {
            resultContainer.setItem(0, ItemStack.EMPTY);
            menu.broadcastChanges();
        }
    }
}
