package uk.co.httpsmmuminecraftsociety.mainmod.miniblocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ResolvableProfile;

public record MiniBlockDefinition(String id, Identifier inputId, String name, String texture) {
    public static final int OUTPUT_COUNT = 8;

    public Item inputItem() {
        return BuiltInRegistries.ITEM.getValue(inputId);
    }

    public ItemStack createOutput() {
        ItemStack output = new ItemStack(Items.PLAYER_HEAD, OUTPUT_COUNT);
        output.set(DataComponents.ITEM_NAME, Component.literal(name));
        output.set(DataComponents.RARITY, Rarity.COMMON);
        output.set(DataComponents.PROFILE, createProfile());
        return output;
    }

    private ResolvableProfile createProfile() {
        JsonObject property = new JsonObject();
        property.addProperty("name", "textures");
        property.addProperty("value", texture);

        JsonArray properties = new JsonArray();
        properties.add(property);

        JsonObject profile = new JsonObject();
        profile.add("properties", properties);

        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, profile)
                .getOrThrow(message -> new IllegalStateException("Invalid profile for mini block " + id + ": " + message));
    }
}
