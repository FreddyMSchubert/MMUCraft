package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.List;

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
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.PHANTOM_MEMBRANE, 1.0F, 0, 10)
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
    }
}
