package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.List;

public class LootTableModifiers {
    public static void onModify(ResourceKey<LootTable> key, LootTable.Builder tableBuilder, LootTableSource source, HolderLookup.Provider registries) {
        if (Identifier.fromNamespaceAndPath("minecraft", "entities/player").equals(key.identifier())) {
            LootPool.Builder poolBuilder = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(createLootTableItem(FakeItems.ID_MAP.get("soul").createItemStack()));
            tableBuilder.withPool(poolBuilder);
        }

        if (Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city").equals(key.identifier())) {
            LootPool.Builder poolBuilder = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(1.0F / 8.0F))
                    .add(createLootTableItem(FakeItems.ID_MAP.get("charm-sculk-phial").createItemStack()));

            tableBuilder.withPool(poolBuilder);
        }
    }

    private static LootPoolSingletonContainer.Builder<?> createLootTableItem(ItemStack stack) {
        LootPoolSingletonContainer.Builder<?> builder =
                LootItem.lootTableItem(stack.getItem());

        for (TypedDataComponent<?> component : stack.getComponents()) {
            applyComponent(builder, component);
        }

        return builder;
    }
    private static <T> void applyComponent(
            LootPoolSingletonContainer.Builder<?> builder,
            TypedDataComponent<T> component
    ) {
        builder.apply(SetComponentsFunction.setComponent(
                component.type(),
                component.value()
        ));
    }

    public static void onModifyDrops(Holder<LootTable> lootTableHolder, LootContext lootContext, List<ItemStack> itemStacks) {
        itemStacks.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK));
    }
}
