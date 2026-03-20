package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

import java.util.List;
import java.util.Objects;

public record FakeItem(
        String title,
        String id,
        Rarity rarity,
        int maxStackSize,
        List<Component> tooltip,
        Item baseItem,
        List<ItemFeature> features
) {
    public FakeItem {
        if (baseItem == null) {
            if (features.stream().anyMatch(EquippableCosmeticItemFeature.class::isInstance)) {
                baseItem = Items.CARVED_PUMPKIN;
            } else {
                baseItem = Items.COMMAND_BLOCK;
            }
        }
    }

    public static FakeItem fromJson(JsonObject json, String filePath) {
        try {
            String title = json.get("title").getAsString();
            String id = json.get("id").getAsString();
            Rarity rarity = JsonUtils.parseRarity(json.get("rarity").getAsString());
            int maxStackSize = json.get("maxStackSize").getAsInt();
            List<Component> tooltip = json.get("tooltips").getAsJsonArray()
                    .asList()
                    .stream()
                    .map(e -> e == null || e.isJsonNull() ? "" : e.getAsString())
                    .map(Component::literal)
                    .map(Component.class::cast)
                    .toList();
            List<ItemFeature> features = ItemFeature.of(json);

            return new FakeItem(
                    title,
                    id,
                    rarity,
                    maxStackSize,
                    tooltip,
                    null,
                    features);
        }
        catch (RuntimeException e) {
            throw new RuntimeException(filePath + ": " + e.getMessage());
        }
    }

    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem, 1);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(id), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        if (tooltip != null && !tooltip.isEmpty() && !tooltip.stream().allMatch(Objects::isNull))
            stack.set(DataComponents.LORE, new ItemLore(tooltip));
        stack.set(DataComponents.RARITY, rarity);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);

        return stack;
    }

    public <T extends ItemFeature> T getFeature(Class<T> featureType) {
        return features.stream()
                .filter(featureType::isInstance)
                .map(featureType::cast)
                .findFirst()
                .orElse(null);
    }
}
