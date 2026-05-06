package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentType;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentTypeManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.List;
import java.util.Map;

public class LootTableModifiers {
    private record LootAddition(
            Identifier tableId,
            String fakeItemId,
            Item vanillaItem,
            float chance,
            int minRolls,
            int maxRolls
    ) {
        public LootAddition {
            boolean hasFake = fakeItemId != null;
            boolean hasVanilla = vanillaItem != null;

            if (hasFake == hasVanilla) {
                throw new IllegalArgumentException(
                        "Exactly one of fakeItemId or vanillaItem must be set"
                );
            }
        }
    }
    private static final List<LootAddition> additions = List.of(
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/player"), "soul", null, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"), "charm-sculk-phial", null, 1.0F / 8.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/bat"), null, Items.PHANTOM_MEMBRANE, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.PHANTOM_MEMBRANE, 1.0F, 0, 10),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/phantom"), null, Items.PHANTOM_MEMBRANE, 1.0F, 1, 2)
    );

    public static void onModifyDrops(Holder<LootTable> lootTableHolder, LootContext lootContext, List<ItemStack> itemStacks) {
        itemStacks.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK));
        itemStacks.removeIf(stack -> stack.is(Items.DIAMOND) && lootTableHolder.unwrapKey().get().identifier().toString().contains("chest"));

        Identifier tableId = lootTableHolder.unwrapKey().map(ResourceKey::identifier).orElse(null);
        for (LootAddition addition : additions) {
            if (!addition.tableId().equals(tableId)) continue;
            if (lootContext.getRandom().nextFloat() >= addition.chance()) continue;

            int rolls = Mth.nextInt(
                    lootContext.getRandom(),
                    addition.minRolls(),
                    addition.maxRolls()
            );

            ItemStack stack = ItemStack.EMPTY;
            if (addition.fakeItemId() != null)
                stack = FakeItems.ID_MAP.get(addition.fakeItemId()).createItemStack();
            else if (addition.vanillaItem() != null)
                stack = addition.vanillaItem().getDefaultInstance();

            stack.setCount(rolls);
            itemStacks.add(stack);
        }

        for (Map.Entry<Identifier, List<EnchantmentType>> entry : EnchantmentTypeManager.byLoottable.entrySet()) {
            if (!entry.getKey().equals(tableId)) continue;
            for (EnchantmentType enchType : entry.getValue()) {
                float chance;
                switch (enchType.loottableRarity) {
                    case Rarity.UNCOMMON -> chance = 0.5f;
                    case Rarity.RARE -> chance = 0.25f;
                    case Rarity.EPIC -> chance = 0.125f;
                    default -> chance = 1.0f;
                }
                if (lootContext.getRandom().nextFloat() >= chance) continue;

                ItemStack stack = Items.ENCHANTED_BOOK.getDefaultInstance();
                Holder<Enchantment> enchantmentHolder = lootContext.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(enchType.enchantment);
                ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                enchantments.set(enchantmentHolder, 1);
                stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());

                itemStacks.add(stack);
            }
        }
    }
}
