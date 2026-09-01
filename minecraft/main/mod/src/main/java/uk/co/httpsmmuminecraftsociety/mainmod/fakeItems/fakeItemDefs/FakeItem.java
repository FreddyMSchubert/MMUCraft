package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record FakeItem(
        String title,
        String id,
        Rarity rarity,
        int maxStackSize,
        boolean fireproof,
        List<Component> tooltip,
        Item baseItem,
        List<ItemFeature> features
) {
    public static FakeItem fromJson(JsonObject json, String filePath) {
        try {
            String title = json.get("title").getAsString();
            String id = json.get("id").getAsString();
            Rarity rarity = JsonUtils.parseRarity(json.get("rarity").getAsString());
            int maxStackSize = json.get("maxStackSize").getAsInt();
            boolean fireproof = json.has("fireproof") && json.get("fireproof").getAsBoolean();
            List<Component> tooltip = json.get("tooltips").getAsJsonArray()
                    .asList()
                    .stream()
                    .map(e -> e == null || e.isJsonNull() ? "" : e.getAsString())
                    .map(MoneyHelper::ReplaceDabloonWords)
                    .map(Component.class::cast)
                    .toList();
            List<ItemFeature> features = ItemFeature.of(json);

            Item baseItem = Items.COMMAND_BLOCK;
            if (features.stream().anyMatch(EquippableCosmeticItemFeature.class::isInstance))
                baseItem = Items.CARVED_PUMPKIN;
            if (json.has("baseItemOverride"))
                baseItem = JsonUtils.resolveItem(json.get("baseItemOverride").getAsString()).get();

            return new FakeItem(
                    title,
                    id,
                    rarity,
                    maxStackSize,
                    fireproof,
                    tooltip,
                    baseItem,
                    features);
        }
        catch (RuntimeException e) {
            throw new RuntimeException(filePath + ": " + e.getMessage());
        }
    }

    public ItemStack createItemStackAtLevel(int charmLevel) {
        ItemStack stack = new ItemStack(baseItem, 1);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(id), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, MoneyHelper.ReplaceDabloonWords(title));
        List<Component> lore = buildLore();
        if (!lore.isEmpty())
            stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.RARITY, rarity);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        if (fireproof) {
            stack.set(DataComponents.DAMAGE_RESISTANT, Items.NETHERITE_INGOT.components().get(DataComponents.DAMAGE_RESISTANT));
        }

        for (ItemFeature feature : features) {
            if (feature instanceof CharmItemFeature cif && charmLevel != -1) {
                cif.apply(stack, charmLevel);
            } else {
                feature.apply(stack);
            }
        }

        FakeItems.wrapTooltip(stack);

        return stack;
    }
    public ItemStack createItemStack() {
        return createItemStackAtLevel(-1);
    }

    public void validate() {
        for (ItemFeature feature : features) {
            feature.validate();
        }
    }

    private List<Component> buildLore() {
        List<Component> lore = new ArrayList<>();
        if (tooltip != null) {
            lore.addAll(tooltip.stream()
                    .filter(Objects::nonNull)
                    .toList());
        }

        List<String> abilityLabels = abilityLabels();
        if (!abilityLabels.isEmpty()) {
            lore.add(Component.literal(String.join(", ", abilityLabels))
                    .withStyle(ChatFormatting.GRAY));
        }

        return List.copyOf(lore);
    }

    private List<String> abilityLabels() {
        List<String> labels = new ArrayList<>();

        if (features.stream().anyMatch(EquippableCosmeticItemFeature.class::isInstance)) {
            labels.add("Hat Cosmetic");
        }
        if (features.stream().anyMatch(DecoBlockItemFeature.class::isInstance)) {
            labels.add("Placeable");
        }
        if (features.stream().anyMatch(EquippableCharmItemFeature.class::isInstance)) {
            labels.add("Wearable Charm");
        }

        return labels;
    }

    public <T extends ItemFeature> T getFeature(Class<T> featureType) {
        return features.stream()
                .filter(featureType::isInstance)
                .map(featureType::cast)
                .findFirst()
                .orElse(null);
    }
}
