package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record TagStackDef(String raw, TagKey<Item> tag) implements StackDef {
    public TagStackDef
    {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        if (tag == null) {
            throw new IllegalArgumentException("tag must not be null");
        }
    }

    @Override
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(tag);
    }

    @Override
    public ItemStack createStack() {
        throw new IllegalStateException("Cannot create an ItemStack from tag '" + tag.location() + "'");
    }

    @Override
    public int specificity() {
        return 1;
    }

    @Override
    public boolean canCreateStack() {
        return false;
    }
}