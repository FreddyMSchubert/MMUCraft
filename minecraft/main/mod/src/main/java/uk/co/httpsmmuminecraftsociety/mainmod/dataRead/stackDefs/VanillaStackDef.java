package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import net.minecraft.world.item.ItemStack;

public record VanillaStackDef(String raw, ParsedVanillaItem item) implements StackDef {
    public VanillaStackDef
    {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
    }

    @Override
    public boolean matches(ItemStack stack) {
        return item.matches(stack);
    }

    @Override
    public ItemStack createStack() {
        return item.createStack(1);
    }

    @Override
    public int specificity() {
        return 2 + item.componentCount();
    }
}
