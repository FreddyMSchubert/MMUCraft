package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.Map;
import java.util.Optional;

public record FakeStackDef(String raw, String fakeItemId, String suffix, String displayNameOverride) implements StackDef
{
    private static final String DUMMY_COMPONENT_PARSE_ITEM = "minecraft:stone";

    public FakeStackDef
    {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        if (fakeItemId == null || fakeItemId.isBlank()) {
            throw new IllegalArgumentException("fakeItemId must not be blank");
        }
        if (suffix == null) {
            throw new IllegalArgumentException("suffix must not be null");
        }
        if (displayNameOverride == null) {
            throw new IllegalArgumentException("displayNameOverride must not be null");
        }
    }

    public static FakeStackDef parse(String raw, String fakeItemId, String suffix, String displayNameOverride) {
        return new FakeStackDef(raw, fakeItemId, suffix, displayNameOverride);
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!FakeItems.isSpecificFakeItem(stack, fakeItemId)) {
            return false;
        }
        if (suffix.isEmpty()) {
            return true;
        }

        return StackComponentPatchUtil.matches(stack, resolvePatch());
    }

    @Override
    public ItemStack createStack() {
        ItemStack stack = FakeItems.createFakeItemStack(fakeItemId, 1);

        if (!suffix.isEmpty()) {
            StackComponentPatchUtil.apply(stack, resolvePatch());
        }

        stack.setCount(1);
        return stack;
    }

    @Override
    public int specificity() {
        return suffix.isEmpty() ? 4 : 5;
    }

    @Override
    public String getDisplayName()
    {
        if (hasDisplayNameOverride()) {
            return displayNameOverride;
        }

        FakeItem fakeItem = FakeItems.requireFakeItem(fakeItemId);
        return fakeItem.title();
    }

    private DataComponentPatch resolvePatch() {
        try {
            ItemInput parsed = new ItemParser(MainMod.getRegistries())
                    .parse(new StringReader(DUMMY_COMPONENT_PARSE_ITEM + suffix));

            DataComponentPatch patch = parsed.components();
            rejectCustomModelDataPatch(patch);
            return patch;
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Invalid fake stack definition '" + raw + "': " + e.getMessage(), e);
        }
    }

    private void rejectCustomModelDataPatch(DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getKey() == DataComponents.CUSTOM_MODEL_DATA) {
                throw new IllegalArgumentException(
                        "Fake item stack descriptions must not override or remove minecraft:custom_model_data: '" + raw + "'"
                );
            }
        }
    }
}
