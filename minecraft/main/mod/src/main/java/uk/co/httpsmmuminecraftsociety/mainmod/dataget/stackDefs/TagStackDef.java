package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

    public static TagStackDef parse(String raw) {
        if (raw.indexOf('[') >= 0) {
            throw new IllegalArgumentException("tag stack descriptions do not support component suffixes: '" + raw + "'");
        }

        Identifier tagId = Identifier.tryParse(raw.substring(1));
        if (tagId == null) {
            throw new IllegalArgumentException("Invalid tag id in stack description: '" + raw + "'");
        }

        return new TagStackDef(raw, TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId));
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
    public String getDisplayName()
    {
        Identifier id = tag.location();
        String translationKey = "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Component.translatable(translationKey).getString();
    }

    @Override
    public boolean canCreateStack() {
        return false;
    }
}