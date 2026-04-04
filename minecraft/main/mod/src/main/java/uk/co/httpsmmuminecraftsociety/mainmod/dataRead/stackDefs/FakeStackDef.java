package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public record FakeStackDef(String raw, String fakeItemId, DataComponentPatch components) implements StackDef
{
    public FakeStackDef
    {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        if (fakeItemId == null || fakeItemId.isBlank()) {
            throw new IllegalArgumentException("fakeItemId must not be blank");
        }
        if (components == null) {
            throw new IllegalArgumentException("components must not be null");
        }
    }

    @Override
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty()
                && FakeItems.isSpecificFakeItem(stack, fakeItemId)
                && StackComponentPatchUtil.matches(stack, components);
    }

    @Override
    public ItemStack createStack() {
        ItemStack stack = FakeItems.createFakeItemStack(fakeItemId, 1);
        StackComponentPatchUtil.apply(stack, components);
        stack.setCount(1);
        return stack;
    }

    @Override
    public int specificity() {
        return 3 + components.size();
    }
}