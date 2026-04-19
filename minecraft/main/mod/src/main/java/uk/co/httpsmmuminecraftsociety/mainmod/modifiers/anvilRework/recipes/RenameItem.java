package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;

public final class RenameItem implements AnvilRecipe
{
    @Override
    public boolean matches(ItemStack left, ItemStack right)
    {
        return right.isEmpty();
    }

    @Override
    public AnvilLogic.Outcome apply(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name)
    {
        ItemStack result = left.copy();
        boolean changed = AnvilUtils.applyRename(result, name);

        if (!changed) {
            return AnvilLogic.Outcome.EMPTY;
        }

        return new AnvilLogic.Outcome(0, ItemStack.EMPTY, ItemStack.EMPTY, result);
    }
}
