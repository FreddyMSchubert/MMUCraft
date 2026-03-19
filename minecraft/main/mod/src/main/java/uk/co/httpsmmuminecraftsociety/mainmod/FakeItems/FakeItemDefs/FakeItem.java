package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FakeItem
{
    private final Item baseItem;
    private final String model_id;
    private final Component title;
    private final List<Component> tooltip;
    private final Rarity rarity;
    private final int maxStackSize;

    public FakeItem(Item baseItem, String model_id, String title, Rarity rarity, int maxStackSize, String... tooltip)
    {
        this.baseItem = baseItem;
        this.model_id = model_id;
        this.title = Component.literal(title);
        this.tooltip = new ArrayList<>();
        for (String loreLine : tooltip)
            if (loreLine != null && !loreLine.isEmpty())
                this.tooltip.add(Component.literal(loreLine));
        this.rarity = rarity;
        this.maxStackSize = maxStackSize;
    }

    public Item getBaseItem() {
        return baseItem;
    }
    public String getModelId() {
        return model_id;
    }
    public Component getTitle() {
        return title;
    }
    public List<Component> getTooltip() {
        return tooltip;
    }
    public Rarity getRarity() {
        return rarity;
    }
    public int getMaxStackSize() {
        return maxStackSize;
    }

    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem, 1);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(model_id), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, title);
        if (tooltip != null && !tooltip.isEmpty() && !tooltip.stream().allMatch(Objects::isNull))
            stack.set(DataComponents.LORE, new ItemLore(tooltip));
        stack.set(DataComponents.RARITY, rarity);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);

        return stack;
    }

    public static FakeItem fromJson(JsonObject root, String sourcePath) {
        JsonObject looks = getLooksObject(root);
        JsonObject behaviour = getBehaviourObject(root);

        String modelType = requiredString(looks, root, "modelType", sourcePath);
        boolean hasEffectId = hasNumber(behaviour, root, "effectId");
        boolean hasConsumableData = hasConsumableData(root);

        if ("hat".equals(modelType)) {
            boolean isTinted = optionalBoolean(looks, root, "isTinted", false);
            return isTinted
                    ? DyeableCosmeticFakeItem.fromJson(root, sourcePath)
                    : CosmeticFakeItem.fromJson(root, sourcePath);
        }

        if ("equippableCharm".equals(modelType)) {
            return EquippableCharmFakeItem.fromJson(root, sourcePath);
        }

        if (hasEffectId && hasConsumableData) {
            return ConsumableCharmFakeItem.fromJson(root, sourcePath);
        }

        if (hasEffectId) {
            return CharmFakeItem.fromJson(root, sourcePath);
        }

        if (hasConsumableData) {
            return ConsumableFakeItem.fromJson(root, sourcePath);
        }

        return BasicFakeItem.fromJson(root, sourcePath);
    }

    protected static JsonObject getLooksObject(JsonObject root) {
        return getObject(root, "looks");
    }

    protected static JsonObject getBehaviourObject(JsonObject root) {
        return getObject(root, "behaviour");
    }

    protected static JsonObject getConsumableObject(JsonObject root) {
        JsonObject behaviour = getBehaviourObject(root);
        return getObject(behaviour, "consumable");
    }

    protected static JsonObject getEquippableObject(JsonObject root) {
        JsonObject behaviour = getBehaviourObject(root);
        return getObject(behaviour, "equippable");
    }

    protected static JsonObject getObject(JsonObject parent, String name) {
        if (parent.has(name) && parent.get(name).isJsonObject()) {
            return parent.getAsJsonObject(name);
        }
        return new JsonObject();
    }

    protected static boolean hasConsumableData(JsonObject root) {
        if (getConsumableObject(root).size() > 0) {
            return true;
        }

        return hasAny(root,
                "consumableId",
                "isDrink",
                "consumeSeconds",
                "canAlwaysEat",
                "hungerBars",
                "saturationBars",
                "directHearts",
                "useRemainderItem",
                "effects"
        ) || hasAny(getBehaviourObject(root),
                "consumableId",
                "isDrink",
                "consumeSeconds",
                "canAlwaysEat",
                "hungerBars",
                "saturationBars",
                "directHearts",
                "useRemainderItem",
                "effects"
        );
    }

    protected static boolean hasAny(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasNumber(JsonObject preferred, JsonObject legacy, String key) {
        JsonElement value = first(preferred, legacy, key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    protected static JsonElement first(JsonObject preferred, JsonObject legacy, String key) {
        if (preferred != null && preferred.has(key)) {
            return preferred.get(key);
        }
        if (legacy != null && legacy.has(key)) {
            return legacy.get(key);
        }
        return null;
    }

    protected static String requiredString(JsonObject preferred, JsonObject legacy, String key, String sourcePath) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException(sourcePath + ": missing string field '" + key + "'");
        }
        return value.getAsString();
    }

    protected static String optionalString(JsonObject preferred, JsonObject legacy, String key, String defaultValue) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return defaultValue;
        }
        return value.getAsString();
    }

    protected static int requiredInt(JsonObject preferred, JsonObject legacy, String key, String sourcePath) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException(sourcePath + ": missing integer field '" + key + "'");
        }
        return value.getAsInt();
    }

    protected static int optionalInt(JsonObject preferred, JsonObject legacy, String key, int defaultValue) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        return value.getAsInt();
    }

    protected static float optionalFloat(JsonObject preferred, JsonObject legacy, String key, float defaultValue) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        return value.getAsFloat();
    }

    protected static boolean optionalBoolean(JsonObject preferred, JsonObject legacy, String key, boolean defaultValue) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return defaultValue;
        }
        return value.getAsBoolean();
    }

    protected static List<String> optionalStringArray(JsonObject preferred, JsonObject legacy, String key) {
        JsonElement value = first(preferred, legacy, key);
        List<String> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) {
            return result;
        }

        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    protected static CommonFields parseCommon(JsonObject root, String sourcePath, int defaultMaxStackSize) {
        JsonObject looks = getLooksObject(root);

        String modelId = requiredString(looks, root, "custom_model_data", sourcePath);
        String title = requiredString(root, root, "title", sourcePath);
        Rarity rarity = parseRarity(requiredString(root, root, "rarity", sourcePath), sourcePath);
        int maxStackSize = optionalInt(root, root, "maxStackSize", defaultMaxStackSize);
        List<String> tooltips = optionalStringArray(root, root, "tooltips");

        return new CommonFields(modelId, title, rarity, maxStackSize, tooltips.toArray(new String[0]));
    }

    protected static Rarity parseRarity(String rarity, String sourcePath) {
        return switch (rarity) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalStateException(sourcePath + ": unsupported rarity '" + rarity + "'");
        };
    }

    protected static EquipmentSlot parseEquipmentSlot(String slot, String sourcePath) {
        return switch (slot) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "body" -> EquipmentSlot.BODY;
            default -> throw new IllegalStateException(sourcePath + ": unsupported equipment slot '" + slot + "'");
        };
    }

    protected static int parseTintColor(JsonObject preferred, JsonObject legacy, String key, int defaultColor, String sourcePath) {
        JsonElement value = first(preferred, legacy, key);
        if (value == null || value.isJsonNull()) {
            return defaultColor;
        }

        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String color = value.getAsString();
            if (color.startsWith("#")) {
                color = color.substring(1);
            }
            if (color.length() != 6) {
                throw new IllegalStateException(sourcePath + ": tintColor must be #RRGGBB or RRGGBB");
            }
            return Integer.parseInt(color, 16);
        }

        if (value.isJsonObject()) {
            JsonObject colorObject = value.getAsJsonObject();
            int r = requiredInt(colorObject, colorObject, "r", sourcePath);
            int g = requiredInt(colorObject, colorObject, "g", sourcePath);
            int b = requiredInt(colorObject, colorObject, "b", sourcePath);
            return (r << 16) | (g << 8) | b;
        }

        throw new IllegalStateException(sourcePath + ": tintColor must be a string or {r,g,b} object");
    }

    protected static ItemStack resolveItemStack(String itemId, String sourcePath) {
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null) {
            throw new IllegalStateException(sourcePath + ": invalid item identifier '" + itemId + "'");
        }

        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == null) {
            throw new IllegalStateException(sourcePath + ": unknown item identifier '" + itemId + "'");
        }

        return item.getDefaultInstance();
    }

    protected static List<MobEffectInstance> parseMobEffects(JsonObject preferred, JsonObject legacy, String key, String sourcePath) {
        JsonElement value = first(preferred, legacy, key);
        List<MobEffectInstance> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) {
            return result;
        }

        for (JsonElement element : value.getAsJsonArray()) {
            JsonObject effectObject = element.getAsJsonObject();
            String effectId = requiredString(effectObject, effectObject, "id", sourcePath);
            Identifier identifier = Identifier.tryParse(effectId);
            if (identifier == null) {
                throw new IllegalStateException(sourcePath + ": invalid effect id '" + effectId + "'");
            }

            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getValue(identifier);
            if (mobEffect == null) {
                throw new IllegalStateException(sourcePath + ": unknown effect id '" + effectId + "'");
            }

            int durationTicks = optionalInt(effectObject, effectObject, "durationTicks", 0);
            int amplifier = optionalInt(effectObject, effectObject, "amplifier", 0);
            boolean ambient = optionalBoolean(effectObject, effectObject, "ambient", false);
            boolean showParticles = optionalBoolean(effectObject, effectObject, "showParticles", true);
            boolean showIcon = optionalBoolean(effectObject, effectObject, "showIcon", true);

            result.add(new MobEffectInstance(mobEffect, durationTicks, amplifier, ambient, showParticles, showIcon));
        }

        return result;
    }

    public record CommonFields(String modelId, String title, Rarity rarity, int maxStackSize, String[] tooltip) {}
}
