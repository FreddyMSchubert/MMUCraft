package uk.co.httpsmmuminecraftsociety.mainmod.mixin.toggles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.CraftingPlayerContext;

@Mixin(CraftingMenu.class)
public abstract class CraftingPlayerContextMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"))
    private static void mainmod$beginCrafting(AbstractContainerMenu menu, ServerLevel level, Player player,
                                              CraftingContainer slots, ResultContainer result,
                                              RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        CraftingPlayerContext.begin(player);
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void mainmod$endCrafting(AbstractContainerMenu menu, ServerLevel level, Player player,
                                            CraftingContainer slots, ResultContainer result,
                                            RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        CraftingPlayerContext.end();
    }
}
