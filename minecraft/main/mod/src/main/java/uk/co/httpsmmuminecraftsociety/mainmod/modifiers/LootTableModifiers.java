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
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.List;

public class LootTableModifiers {
    private static final Identifier PLAYER_ID = Identifier.fromNamespaceAndPath("minecraft", "entities/player");

    public static void onModify(ResourceKey<LootTable> key, LootTable.Builder tableBuilder, LootTableSource source, HolderLookup.Provider registries)
    {
        // make players drop souls
        if (PLAYER_ID.equals(key.identifier())) {
            ItemStack soulItemStack = FakeItems.MODEL_ID_MAP.get("soul").createItemStack();

            LootPoolSingletonContainer.Builder<?> soulLootItem =
                    LootItem.lootTableItem(Items.COMMAND_BLOCK);

            for (TypedDataComponent<?> component : soulItemStack.getComponents()) {
                applyComponent(soulLootItem, component);
            }

            LootPool.Builder poolBuilder = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(soulLootItem);

            tableBuilder.withPool(poolBuilder);
        }

        // remove all enchanted books from loottables

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

    public static void onModifyDrops(Holder<LootTable> lootTableHolder, LootContext lootContext, List<ItemStack> itemStacks)
    {
        itemStacks.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK));
    }
}
