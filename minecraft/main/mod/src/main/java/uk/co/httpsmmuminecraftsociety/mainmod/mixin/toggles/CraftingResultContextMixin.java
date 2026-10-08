package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.CraftingPlayerContext;

@Mixin(ResultSlot.class)
public abstract class CraftingResultContextMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void mainmod$beginCrafting(Player player, ItemStack carried, CallbackInfo ci) {
        CraftingPlayerContext.begin(player);
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void mainmod$endCrafting(Player player, ItemStack carried, CallbackInfo ci) {
        CraftingPlayerContext.end();
    }
}
