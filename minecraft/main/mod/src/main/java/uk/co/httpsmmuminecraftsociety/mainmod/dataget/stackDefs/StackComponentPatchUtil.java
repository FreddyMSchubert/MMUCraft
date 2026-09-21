package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

final class StackComponentPatchUtil {
    private StackComponentPatchUtil() {}

    static boolean matches(ItemStack stack, DataComponentPatch patch) {
        var split = patch.split();
        for (var component : split.added()) {
            if (!Objects.equals(stack.get(component.type()), component.value())) return false;
        }
        return split.removed().stream().noneMatch(stack::has);
    }

    static void apply(ItemStack stack, DataComponentPatch patch) {
        stack.applyComponents(patch);
    }
}
