package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class ModEnchantments
{
    public static final ResourceKey<Enchantment> CHARM_BOOST = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_boost"));
    public static final ResourceKey<Enchantment> SOULBOUND = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "soulbound"));
}
