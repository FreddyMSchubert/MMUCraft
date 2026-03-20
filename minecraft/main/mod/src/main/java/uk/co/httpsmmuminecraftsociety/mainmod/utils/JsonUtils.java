package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public class JsonUtils
{
    public static ItemStack resolveItemStack(String itemId) {
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null) {
            throw new IllegalStateException("Invalid item identifier '" + itemId + "'");
        }
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new IllegalStateException("Unknown item identifier '" + itemId + "'");
        }
        return BuiltInRegistries.ITEM.getValue(identifier).getDefaultInstance();
    }

    public static MobEffectInstance parseMobEffect(JsonObject json) {
        String effectId = json.get("id").getAsString();
        Identifier identifier = Identifier.tryParse(effectId);
        if (identifier == null) {
            throw new IllegalStateException("Invalid effect id '" + effectId + "'");
        }

        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, identifier);
        Holder<MobEffect> mobEffect = BuiltInRegistries.MOB_EFFECT
                .get(key)
                .orElseThrow(() -> new IllegalStateException("Unknown effect id '" + effectId + "'"));

        int durationTicks = json.get("durationTicks").getAsInt();
        int amplifier = json.get("amplifier").getAsInt();
        boolean ambient = json.get("ambient").getAsBoolean();
        boolean showParticles = json.get("showParticles").getAsBoolean();
        boolean showIcon = json.get("showIcon").getAsBoolean();

        return new MobEffectInstance(mobEffect, durationTicks, amplifier, ambient, showParticles, showIcon);
    }

    public static int parseTintColor(String hex) {
        if (hex.length() != 7) {
            throw new IllegalStateException("TintColor should be #RRGGBB, is " + hex + ".");
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    public static EquipmentSlot parseEquipmentSlot(String slot) {
        return switch (slot) {
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> throw new IllegalStateException("Unsupported equipment slot '" + slot + "'");
        };
    }

    public static Rarity parseRarity(String value) {
        return switch (value.toLowerCase()) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalArgumentException(
                    "Unknown rarity: " + value
            );
        };
    }
}
