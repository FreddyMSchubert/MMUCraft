package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;

import java.util.List;

public sealed interface AnvilRecipe
    permits RepairSameItem, RenameItem, RepairMaterial, ApplyEnchBook
{
    boolean matches(ItemStack left, ItemStack right);
    AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name);

    List<AnvilRecipe> RECIPE_ORDER = List.of(
            new RenameItem(),
            new RepairSameItem(),
            new RepairMaterial(),
            new ApplyEnchBook()
    );

    static @Nullable AnvilRecipe getFirstMatching(ItemStack left, ItemStack right) {
        for (AnvilRecipe recipe : RECIPE_ORDER) {
            if (recipe.matches(left, right)) {
                return recipe;
            }
        }
        return null;
    }
}
