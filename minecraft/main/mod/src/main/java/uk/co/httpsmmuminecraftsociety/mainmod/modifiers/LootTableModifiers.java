package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettings;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;
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
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.DRAGON_EGG, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/phantom"), null, Items.PHANTOM_MEMBRANE, 1.0F, 1, 2)
    );

    private record PlayerLootAddition(
            List<String> playerNames,
            String fakeItemId,
            Item vanillaItem,
            float chance,
            int minRolls,
            int maxRolls
    ) {
        public PlayerLootAddition {
            boolean hasFake = fakeItemId != null;
            boolean hasVanilla = vanillaItem != null;

            if (hasFake == hasVanilla) {
                throw new IllegalArgumentException(
                        "Exactly one of fakeItemId or vanillaItem must be set"
                );
            }
        }

        public boolean matches(Player player) {
            String name = player.getGameProfile().name();

            return playerNames.stream()
                    .anyMatch(playerName -> playerName.equalsIgnoreCase(name));
        }
    }
    private static final List<PlayerLootAddition> playerAdditions = List.of(
            new PlayerLootAddition(List.of("archiedobbo"), "disc-9am", null, 1.0F, 1, 1),
            new PlayerLootAddition(List.of("archiedobbo"), null, Items.MANGROVE_DOOR, 1.0F, 1, 1),
            new PlayerLootAddition(List.of("oderzo"), null, Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, 1, 1, 3),
            new PlayerLootAddition(List.of("CalRay2"), null, Items.SLIME_BALL, 0.5f, 1, 5),
            new PlayerLootAddition(List.of("CalRay2"), "beer", null, 0.5f, 1, 5),
            new PlayerLootAddition(List.of("miaalicexoxo"), null, Items.LILY_OF_THE_VALLEY, 1, 1, 1),
            new PlayerLootAddition(List.of("MerlinSpace"), null, Items.APPLE, 1, 1, 1),
            new PlayerLootAddition(List.of("HannahLucyyy"), null, Items.CAKE, 0.2f, 1, 1)
    );
    private static void addPlayerSpecificDrops(
            Identifier tableId,
            LootContext lootContext,
            List<ItemStack> itemStacks
    ) {
        if (!Identifier.fromNamespaceAndPath("minecraft", "entities/player").equals(tableId)) {
            return;
        }

        Entity entity = lootContext.getOptionalParameter(LootContextParams.THIS_ENTITY);

        if (!(entity instanceof Player player)) {
            return;
        }

        for (PlayerLootAddition addition : playerAdditions) {
            if (!addition.matches(player)) continue;
            if (lootContext.getRandom().nextFloat() >= addition.chance()) continue;

            int rolls = Mth.nextInt(
                    lootContext.getRandom(),
                    addition.minRolls(),
                    addition.maxRolls()
            );

            if (rolls <= 0) continue;

            ItemStack stack;

            if (addition.fakeItemId() != null) {
                stack = FakeItems.ID_MAP.get(addition.fakeItemId()).createItemStack();
            } else {
                stack = addition.vanillaItem().getDefaultInstance();
            }

            stack.setCount(rolls);
            itemStacks.add(stack);
        }
    }

    public static void onModifyDrops(Holder<LootTable> lootTableHolder, LootContext lootContext, List<ItemStack> itemStacks) {
        itemStacks.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK));
        itemStacks.removeIf(stack -> stack.is(Items.DIAMOND) && lootTableHolder.unwrapKey().get().identifier().toString().contains("chest"));

        Identifier tableId = lootTableHolder.unwrapKey().map(ResourceKey::identifier).orElse(null);

        addPlayerSpecificDrops(tableId, lootContext, itemStacks);

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

        for (Map.Entry<Identifier, List<EnchantmentSettings>> entry : EnchantmentSettingsManager.byLoottable.entrySet()) {
            if (!entry.getKey().equals(tableId)) continue;
            for (EnchantmentSettings enchType : entry.getValue()) {
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
