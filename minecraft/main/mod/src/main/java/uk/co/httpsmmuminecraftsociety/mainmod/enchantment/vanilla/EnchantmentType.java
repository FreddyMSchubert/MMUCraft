package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentType
{
    public ResourceKey<Enchantment> enchantment;
    public boolean defaultEnchantingTableAvailable;
    public List<Identifier> foundInLoottables;
    public Rarity loottableRarity;

    public EnchantmentType(ResourceKey<Enchantment> enchantment, boolean defaultEnchantingTableAvailable, List<String> foundInLoottables, Rarity loottableRarity)
    {
        this.enchantment = enchantment;
        this.defaultEnchantingTableAvailable = defaultEnchantingTableAvailable;
        this.foundInLoottables = new ArrayList<>();
        for (String foundInLoottable : foundInLoottables)
        {
            if (!foundInLoottable.contains("/"))
                foundInLoottable = "chests/" + foundInLoottable;
            this.foundInLoottables.add(Identifier.withDefaultNamespace(foundInLoottable));
        }
        this.loottableRarity = loottableRarity;
    }
}
