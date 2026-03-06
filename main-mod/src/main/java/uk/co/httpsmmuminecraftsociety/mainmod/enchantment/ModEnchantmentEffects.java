package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class ModEnchantmentEffects {
    public static MapCodec<CharmBoostEnchantmentEffect> LIGHTNING_EFFECT = register("charm_boost", CharmBoostEnchantmentEffect.CODEC);

    private static <T extends EnchantmentEntityEffect> MapCodec<T> register(String id, MapCodec<T> codec) {
        return Registry.register(BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, id), codec);
    }

    public static void registerModEnchantmentEffects() {
        MainMod.LOGGER.info("Registering EnchantmentEffects for" + MainMod.MOD_ID);
    }
}