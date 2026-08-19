package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentSettings
{
    public ResourceKey<Enchantment> enchantment;

    public int maxNormalGearLevel = -1;
    public int maxEnderiteLevel = -1;

    public List<Identifier> foundInLoottables = new ArrayList<>();
    public Rarity loottableRarity = Rarity.COMMON;
    public float loottableChance = -1.0f;

    public EnchantmentSettings(ResourceKey<Enchantment> enchantment)
    {
        this.enchantment = enchantment;
    }

    public EnchantmentSettings maxLevels(int maxNormalGearLevel, int maxEnderiteLevel) {
        this.maxNormalGearLevel = maxNormalGearLevel;
        this.maxEnderiteLevel = maxEnderiteLevel;
        return this;
    }

    public EnchantmentSettings inLoottable(String loottable) {
        foundInLoottables.add(Identifier.withDefaultNamespace(loottable));
        return this;
    }
    public EnchantmentSettings inLoottable(List<String> loottables) {
        for (String loottable : loottables)
            this.inLoottable(loottable);
        return this;
    }
    public EnchantmentSettings inLoottable(String... loottables) {
        for (String loottable : loottables)
            this.inLoottable(loottable);
        return this;
    }

    public EnchantmentSettings rarity(Rarity rarity) {
        loottableRarity = rarity;
        return this;
    }

    public EnchantmentSettings loottableChance(float chance) {
        loottableChance = chance;
        return this;
    }

    public List<Identifier> validateLoottables(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<LootTable> lootTables = registries.lookupOrThrow(Registries.LOOT_TABLE);
        List<Identifier> invalidLoottables = foundInLoottables.stream()
            .filter(loottable -> lootTables.get(ResourceKey.create(Registries.LOOT_TABLE, loottable)).isEmpty())
            .toList();

        if (!invalidLoottables.isEmpty()) {
            foundInLoottables.removeAll(invalidLoottables);
        }

        return invalidLoottables;
    }
}
