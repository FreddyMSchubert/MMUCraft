package uk.co.httpsmmuminecraftsociety.mainmod.playerpotions;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public final class PlayerPotions {
    public static final int DEFAULT_TICKS = 5 * 60 * 20;
    public static final int EXTENDED_TICKS = 20 * 60 * 20;
    private static final String PREFIX = "mainmod_player:";
    private PlayerPotions() {}

    public static boolean isDeathHead(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        return stack.is(Items.PLAYER_HEAD) && profile != null
                && profile.partialProfile().id() != null
                && stack.getOrDefault(DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag()
                        .getBooleanOr("mainmod_death_head", false);
    }

    public static ItemStack brew(ItemStack input, ItemStack reagent) {
        if (isDeathHead(reagent)) {
            GameProfile profile = reagent.get(DataComponents.PROFILE).partialProfile();
            String identity = encode(profile, DEFAULT_TICKS);
            ItemStack result = new ItemStack(Items.POTION);
            write(result, identity);
            SkinColors.color(profile, result);
            return result;
        }
        ItemStack result = input.copyWithCount(1);
        String identity = identity(input);
        if (reagent.is(Items.REDSTONE)) write(result, withDuration(identity, EXTENDED_TICKS));
        else if (reagent.is(Items.GUNPOWDER) || reagent.is(Items.DRAGON_BREATH)) {
            ItemStack converted = new ItemStack(reagent.is(Items.GUNPOWDER)
                    ? Items.SPLASH_POTION : Items.LINGERING_POTION);
            converted.applyComponents(result.getComponentsPatch());
            result = converted;
        }
        return result;
    }

    public static boolean isDisguise(ItemStack stack) {
        return identity(stack) != null;
    }

    public static String identity(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents == null ? null : identity(contents);
    }

    public static String identity(PotionContents contents) {
        if (contents == null) return null;
        String value = contents.customName().orElse("");
        if (!value.startsWith(PREFIX)) return null;
        return decode(value) != null ? value : null;
    }

    public static GameProfile decode(String identity) {
        if (identity == null || identity.length() > 30000 || !identity.startsWith(PREFIX)) return null;
        try {
            String[] parts = identity.substring(PREFIX.length()).split(":", -1);
            if (parts.length != 5 || !validDuration(parts[4])) return null;
            UUID id = UUID.fromString(parts[0]);
            String name = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            if (!name.matches("[A-Za-z0-9_]{1,16}")) return null;
            String texture = new String(Base64.getUrlDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8);
            String signature = new String(Base64.getUrlDecoder().decode(parts[3]), java.nio.charset.StandardCharsets.UTF_8);
            if (texture.length() > 12000 || signature.length() > 12000) return null;
            var properties = ArrayListMultimap.<String, Property>create();
            if (!texture.isEmpty()) properties.put("textures", signature.isEmpty()
                    ? new Property("textures", texture) : new Property("textures", texture, signature));
            return new GameProfile(id, name, new PropertyMap(properties));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static int duration(String identity) {
        if (decode(identity) == null) return 0;
        return Integer.parseInt(identity.substring(identity.lastIndexOf(':') + 1));
    }

    private static boolean validDuration(String value) {
        return value.equals(Integer.toString(DEFAULT_TICKS))
                || value.equals(Integer.toString(EXTENDED_TICKS));
    }

    private static String withDuration(String identity, int ticks) {
        return identity.substring(0, identity.lastIndexOf(':') + 1) + ticks;
    }

    private static String encode(GameProfile profile, int ticks) {
        Property texture = profile.properties().get("textures").stream().findFirst().orElse(null);
        return PREFIX + profile.id() + ":" + base64(profile.name()) + ":"
                + base64(texture == null ? "" : texture.value()) + ":"
                + base64(texture == null || texture.signature() == null ? "" : texture.signature())
                + ":" + ticks;
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void write(ItemStack stack, String identity) {
        PotionContents old = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(),
                old.customColor(), java.util.List.of(),
                Optional.of(identity)));
        GameProfile profile = decode(identity);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Potion of " + profile.name()));
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal("Disguise: " + duration(identity) / 1200 + " minutes"))));
    }
}
