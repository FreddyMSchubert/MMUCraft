package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

record ParsedVanillaItem(String raw, Holder<Item> item, DataComponentPatch components) {
    ParsedVanillaItem {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw item string must not be blank");
        }
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (components == null) {
            throw new IllegalArgumentException("components must not be null");
        }
    }

    static ParsedVanillaItem parse(HolderLookup.Provider registries, String raw) {
        try {
            ItemInput parsed = new ItemParser(registries).parse(new StringReader(raw));
            return new ParsedVanillaItem(raw, parsed.item(), parsed.components());
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid item stack description '" + raw + "': " + e.getMessage(), e);
        }
    }

    boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() != item.value()) {
            return false;
        }
        return StackComponentPatchUtil.matches(stack, components);
    }

    ItemStack createStack(int count) {
        try {
            return new ItemInput(item, components).createItemStack(count);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Invalid item result '" + raw + "'", e);
        }
    }

    int componentCount() {
        return components.size();
    }
}
