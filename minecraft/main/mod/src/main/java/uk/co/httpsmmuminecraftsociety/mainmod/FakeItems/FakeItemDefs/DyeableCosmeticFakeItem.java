package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;

import java.util.Map;

public class DyeableCosmeticFakeItem extends CosmeticFakeItem
{
    private final int dyeColor;

    private static final Map<String, Integer> LEGACY_DEFAULT_TINTS = Map.ofEntries(
            Map.entry("cosmetic-hat-spartan-helmet", 0x0000FF),
            Map.entry("cosmetic-hat-amogus-hat", 0xFF0000),
            Map.entry("cosmetic-hat-devil-horns", 0xFF0000),
            Map.entry("cosmetic-hat-ice-cream", 0x835432),
            Map.entry("cosmetic-hat-plunger", 0xFF0000),
            Map.entry("cosmetic-hat-beanie", 0xA600FF),
            Map.entry("cosmetic-hat-mohawk", 0x24DDBA),
            Map.entry("cosmetic-hat-moustache-fancy", 0x835432),
            Map.entry("cosmetic-hat-moustache-bushy", 0x835432),
            Map.entry("cosmetic-hat-moustache-square", 0x835432),
            Map.entry("cosmetic-hat-candle", 0xD8D29D)
    );

    public DyeableCosmeticFakeItem(String model_id, String title, Rarity rarity, int dyeColor, String... tooltip)
    {
        super(model_id, title, rarity, tooltip);

        this.dyeColor = dyeColor;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
        stack.set(DataComponents.LORE, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(Component.literal("Dyeable")));
        return stack;
    }

    public static DyeableCosmeticFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 1);
        JsonObject looks = getLooksObject(root);

        int fallbackColor = LEGACY_DEFAULT_TINTS.getOrDefault(common.modelId(), 0xFFFFFF);
        int tintColor = parseTintColor(looks, root, "tintColor", fallbackColor, sourcePath);

        return new DyeableCosmeticFakeItem(common.modelId(), common.title(), common.rarity(), tintColor, common.tooltip());
    }
}
