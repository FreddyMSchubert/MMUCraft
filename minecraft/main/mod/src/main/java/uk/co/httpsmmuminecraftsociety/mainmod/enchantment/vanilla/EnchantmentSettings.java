package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.TriState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentSettings
{
    public ResourceKey<Enchantment> enchantment;

    public int maxEnchantingTableLevel = -1;

    public List<Identifier> foundInLoottables = new ArrayList<>();
    public Rarity loottableRarity = Rarity.COMMON;

    public TriState usesFakeItemToDupe = TriState.DEFAULT;
    public Item normalDupeItem = Items.FIRE_CORAL;
    public String fakeDupeItemId;

    public EnchantmentSettings(ResourceKey<Enchantment> enchantment)
    {
        this.enchantment = enchantment;
    }

    public EnchantmentSettings maxEnchTableLvl(int level) {
        this.maxEnchantingTableLevel = level;
        return this;
    }

    public EnchantmentSettings inLoottable(String loottable) {
        if (!loottable.contains("/"))
            loottable = "chests/" + loottable;
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

    public EnchantmentSettings dupedWithVanillaItem(Item item) {
        usesFakeItemToDupe = TriState.FALSE;
        normalDupeItem = item;
        return this;
    }
    public EnchantmentSettings dupedWithFakeItem(String itemId) {
        if (!FakeItems.ID_MAP.containsKey(itemId))
            throw new RuntimeException("No such fake item id: " + itemId);

        usesFakeItemToDupe = TriState.TRUE;
        fakeDupeItemId = itemId;
        return this;
    }
}
