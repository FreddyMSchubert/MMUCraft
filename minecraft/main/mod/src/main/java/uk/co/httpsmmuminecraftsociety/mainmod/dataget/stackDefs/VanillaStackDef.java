package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

import java.util.Optional;

public record VanillaStackDef(String raw, String itemId, String suffix, String displayNameOverride) implements StackDef {
    public VanillaStackDef
    {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (suffix == null) {
            throw new IllegalArgumentException("suffix must not be null");
        }
        if (displayNameOverride == null) {
            throw new IllegalArgumentException("displayNameOverride must not be null");
        }

        Optional<Item> item = JsonUtils.resolveItem(itemId);
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Unknown item id: " + itemId);
        }
    }

    public static VanillaStackDef parse(String raw, String itemId, String suffix, String displayNameOverride) {
        return new VanillaStackDef(raw, itemId, suffix, displayNameOverride);
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemStackTemplate template = resolveTemplate();
        return stack.getItem() == template.item().value()
                && StackComponentPatchUtil.matches(stack, template.components());
    }

    @Override
    public ItemStack createStack() {
        return resolveTemplate().create();
    }

    @Override
    public int specificity() {
        return suffix.isEmpty() ? 2 : 3;
    }

    @Override
    public String getDisplayName()
    {
        if (hasDisplayNameOverride()) {
            return displayNameOverride;
        }

        Item item = JsonUtils.resolveItem(itemId)
                .orElseThrow(() -> new IllegalStateException("Unknown item id: " + itemId));
        return Component.translatable(item.getDescriptionId()).getString();
    }

    private ItemStackTemplate resolveTemplate() {
        try {
            ItemInput parsed = new ItemParser(MainMod.getRegistries()).parse(new StringReader(itemId + suffix));
            return new ItemStackTemplate(parsed.item(), 1, parsed.components());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Invalid vanilla stack definition '" + raw + "': " + e.getMessage(), e);
        }
    }
}
