package uk.co.httpsmmuminecraftsociety.mainmod.recipe.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.List;

public final class FakeRecipeUtil {
    private FakeRecipeUtil() {}

    public static boolean isFakeItem(ItemStack stack, String fakeItemId) {
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return cmd != null && cmd.strings().contains(fakeItemId);
    }

    public static boolean isKnownFakeItem(String fakeItemId) {
        return FakeItems.ID_MAP.containsKey(fakeItemId);
    }

    static FakeItem requireFakeItem(String fakeItemId) {
        FakeItem fakeItem = FakeItems.ID_MAP.get(fakeItemId);
        if (fakeItem == null) {
            throw new IllegalArgumentException("Unknown fakeitem id: " + fakeItemId);
        }
        return fakeItem;
    }

    public static ItemStack createFakeItemStack(String fakeItemId, int count) {
        ItemStack stack = requireFakeItem(fakeItemId).createItemStack();
        stack.setCount(count);
        return stack;
    }

    public static List<String> trimPattern(List<String> rawPattern) {
        int top = 0;
        int bottom = rawPattern.size() - 1;

        while (top <= bottom && isBlankRow(rawPattern.get(top))) top++;
        while (bottom >= top && isBlankRow(rawPattern.get(bottom))) bottom--;

        if (top > bottom) {
            return List.of();
        }

        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        for (int i = top; i <= bottom; i++) {
            String row = rawPattern.get(i);
            int first = firstNonSpace(row);
            int last = lastNonSpace(row);
            if (first == -1) continue;
            left = Math.min(left, first);
            right = Math.max(right, last);
        }

        if (left == Integer.MAX_VALUE) {
            return List.of();
        }

        List<String> trimmed = new ArrayList<>();
        for (int i = top; i <= bottom; i++) {
            trimmed.add(rawPattern.get(i).substring(left, right + 1));
        }
        return List.copyOf(trimmed);
    }

    private static boolean isBlankRow(String row) {
        return firstNonSpace(row) == -1;
    }

    private static int firstNonSpace(String row) {
        for (int i = 0; i < row.length(); i++) {
            if (row.charAt(i) != ' ') return i;
        }
        return -1;
    }

    private static int lastNonSpace(String row) {
        for (int i = row.length() - 1; i >= 0; i--) {
            if (row.charAt(i) != ' ') return i;
        }
        return -1;
    }
}
