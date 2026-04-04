package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class StackComponentPatchUtil {
    private StackComponentPatchUtil() {}

    static boolean matches(ItemStack stack, DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (!matchesUnchecked(stack, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    static void apply(ItemStack stack, DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            applyUnchecked(stack, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchesUnchecked(ItemStack stack,
                                            DataComponentType<?> type,
                                            Optional<?> expected) {
        return matches(stack, (DataComponentType) type, (Optional) expected);
    }

    private static <T> boolean matches(ItemStack stack,
                                       DataComponentType<T> type,
                                       Optional<T> expected) {
        if (expected.isPresent()) {
            return Objects.equals(stack.get(type), expected.get());
        }
        return !stack.has(type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyUnchecked(ItemStack stack,
                                       DataComponentType<?> type,
                                       Optional<?> value) {
        apply(stack, (DataComponentType) type, (Optional) value);
    }

    private static <T> void apply(ItemStack stack,
                                  DataComponentType<T> type,
                                  Optional<T> value) {
        if (value.isPresent()) {
            stack.set(type, value.get());
        } else {
            stack.remove(type);
        }
    }
}
