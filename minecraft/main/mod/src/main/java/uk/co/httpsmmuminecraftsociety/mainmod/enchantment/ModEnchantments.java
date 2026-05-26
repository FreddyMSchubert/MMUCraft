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

    public static final ResourceKey<Enchantment> CURSE_FRAGILITY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_fragility"));
    public static final ResourceKey<Enchantment> CURSE_DECAY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_decay"));
    public static final ResourceKey<Enchantment> CURSE_DROUGHT = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_drought"));
    public static final ResourceKey<Enchantment> CURSE_IRREPERABILITY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_irreperability"));
    public static final ResourceKey<Enchantment> CURSE_OMEN = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_omen"));
    public static final ResourceKey<Enchantment> CURSE_WASTEFULNESS = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_wastefulness"));
    public static final ResourceKey<Enchantment> CURSE_WEARINESS = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_weariness"));
    public static final ResourceKey<Enchantment> CURSE_NECROSIS = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_necrosis"));
    public static final ResourceKey<Enchantment> CURSE_DULLNESS = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_dullness"));
    public static final ResourceKey<Enchantment> CURSE_STORMCALLING = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_stormcalling"));
    public static final ResourceKey<Enchantment> CURSE_WEAKNESS_FIRE = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_weakness_fire"));
    public static final ResourceKey<Enchantment> CURSE_WEAKNESS_FALL = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_weakness_fall"));
    public static final ResourceKey<Enchantment> CURSE_WEAKNESS_BLAST = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_weakness_blast"));
    public static final ResourceKey<Enchantment> CURSE_WEAKNESS_PROJECTILE = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "curse_weakness_projectile"));
}
