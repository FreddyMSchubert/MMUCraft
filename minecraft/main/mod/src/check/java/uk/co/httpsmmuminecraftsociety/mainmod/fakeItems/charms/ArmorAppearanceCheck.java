package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.waypoints.Waypoint;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureToggle;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.ToggleArmorAppearanceRecipe;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.Map;

public final class ArmorAppearanceCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());
        ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
        assert !CosmeticsManager.determineCosmeticType(pumpkin).isCosmetic();
        CosmeticsManager.normalizeCosmeticHead(pumpkin);
        assert pumpkin.get(DataComponents.EQUIPPABLE).cameraOverlay().isPresent();
        ItemStack cosmetic = pumpkin.copy();
        cosmetic.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of("test-cosmetic"), List.of()));
        var reducedVisibility = Items.ZOMBIE_HEAD.components().get(DataComponents.MOB_VISIBILITY);
        assert reducedVisibility != null;
        cosmetic.set(DataComponents.MOB_VISIBILITY, reducedVisibility);
        assert CosmeticsManager.determineCosmeticType(cosmetic).isCosmetic();
        new EquippableCosmeticItemFeature().apply(cosmetic);
        assert cosmetic.get(DataComponents.EQUIPPABLE).cameraOverlay().isEmpty();
        assert !cosmetic.has(DataComponents.MOB_VISIBILITY);
        assert cosmetic.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().noneMatch(entry ->
                entry.matches(Attributes.WAYPOINT_TRANSMIT_RANGE,
                        Waypoint.WAYPOINT_TRANSMIT_RANGE_HIDE_MODIFIER.id()));
        var chestplate = Items.DIAMOND_CHESTPLATE.builtInRegistryHolder();
        var netheriteChestplate = Items.NETHERITE_CHESTPLATE.builtInRegistryHolder();
        List<Holder<Item>> helmets = List.of(Items.LEATHER_HELMET.builtInRegistryHolder(),
                Items.COPPER_HELMET.builtInRegistryHolder(), Items.GOLDEN_HELMET.builtInRegistryHolder(),
                Items.CHAINMAIL_HELMET.builtInRegistryHolder(), Items.IRON_HELMET.builtInRegistryHolder(),
                Items.DIAMOND_HELMET.builtInRegistryHolder(), Items.NETHERITE_HELMET.builtInRegistryHolder(),
                Items.TURTLE_HELMET.builtInRegistryHolder());
        var itemRegistry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(itemRegistry, false);
        itemRegistry.bindTags(Map.<TagKey<Item>, List<Holder<Item>>>of(
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS, List.of(chestplate, netheriteChestplate),
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND, List.of(chestplate),
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE, List.of(netheriteChestplate),
                ModItemTagProvider.SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS, List.of(chestplate),
                ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS, helmets
        ));
        var refreshTags = MappedRegistry.class.getDeclaredMethod("refreshTagsInHolders");
        refreshTags.setAccessible(true);
        refreshTags.invoke(itemRegistry);
        frozen.setBoolean(itemRegistry, true);
        var feature = new EquippableCharmItemFeature(
                EquippableCharmItemFeature.createEquippableSettings("test_charm__charm", EquipmentSlot.CHEST));
        FakeItems.CHARM_ID_MAP = Map.of(7, new FakeItem("Test charm", "test-charm", Rarity.COMMON,
                1, false, false, "", List.of(), Items.HEART_OF_THE_SEA, List.of(feature)));

        var pane = new ToggleArmorAppearanceRecipe(true);
        var glass = new ToggleArmorAppearanceRecipe(false);
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.setDamageValue(12);
        CharmStackData.setStoredCharms(armor, List.of(new StoredCharmData(7, 1)));
        CharmorManager.updateArmorTooltip(armor);
        assert asset(armor).equals("mmu_pack:test_charm__diamond")
                : asset(armor) + " combinable=" + armor.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)
                + " diamond=" + armor.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND);
        assert armor.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 2;
        var paneInput = CraftingInput.of(2, 1, List.of(armor, new ItemStack(Items.GLASS_PANE)));
        var glassInput = CraftingInput.of(2, 1, List.of(armor, new ItemStack(Items.GLASS)));
        assert !pane.matches(paneInput, null) && pane.assemble(paneInput).isEmpty();
        assert !glass.matches(glassInput, null) && glass.assemble(glassInput).isEmpty();
        assert !glass.matches(CraftingInput.of(2, 1,
                List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.GLASS))), null);

        setSoaring(true);
        CharmorManager.updateArmorTooltip(armor);
        assert armor.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 4;
        for (var excluded : List.of(Items.CARVED_PUMPKIN)) {
            for (var recipe : List.of(pane, glass)) {
                var ingredient = recipe == pane ? Items.GLASS_PANE : Items.GLASS;
                var input = CraftingInput.of(2, 1, List.of(new ItemStack(excluded), new ItemStack(ingredient)));
                assert !recipe.matches(input, null) && recipe.assemble(input).isEmpty();
            }
        }
        for (var entry : Map.of(
                Items.LEATHER_HELMET, "leather", Items.COPPER_HELMET, "copper",
                Items.GOLDEN_HELMET, "gold", Items.CHAINMAIL_HELMET, "chainmail",
                Items.IRON_HELMET, "iron", Items.DIAMOND_HELMET, "diamond",
                Items.NETHERITE_HELMET, "netherite", Items.TURTLE_HELMET, "turtle_scute").entrySet()) {
            ItemStack helmet = new ItemStack(entry.getKey());
            helmet.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Personal note"))));
            var helmetPane = CraftingInput.of(2, 1, List.of(helmet, new ItemStack(Items.GLASS_PANE)));
            assert !pane.matches(helmetPane, null) && pane.assemble(helmetPane).isEmpty();
            helmet = craft(glass, helmet, Items.GLASS);
            assert asset(helmet).equals("mmu_pack:armor_minified__" + entry.getValue());
            assert helmet.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
                    .map(Component::getString).toList().equals(List.of("Personal note", "Armor: Minified"));
            helmet = craft(glass, helmet, Items.GLASS);
            assert helmet.get(DataComponents.EQUIPPABLE).equals(entry.getKey().components().get(DataComponents.EQUIPPABLE));
            assert helmet.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
                    .map(Component::getString).toList().equals(List.of("Personal note", "Armor: Full"));
        }

        armor = craft(pane, armor, Items.GLASS_PANE);
        assert CharmorManager.charmsHidden(armor) && !CharmorManager.armorMinified(armor);
        assert armor.get(DataComponents.EQUIPPABLE).equals(Items.DIAMOND_CHESTPLATE.components().get(DataComponents.EQUIPPABLE));
        armor = craft(glass, armor, Items.GLASS);
        assert asset(armor).equals("mmu_pack:armor_minified__diamond");
        ItemStack hiddenMinified = armor;
        armor = craft(pane, armor, Items.GLASS_PANE);
        assert asset(armor).equals("mmu_pack:test_charm__diamond__minified");
        armor = craft(glass, armor, Items.GLASS);
        assert asset(armor).equals("mmu_pack:test_charm__diamond");
        assert armor.getDamageValue() == 12 && CharmStackData.getStoredCharms(armor).size() == 1;
        assert armor.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().getLast().getString().equals("Armor: Full");
        assert !glass.matches(CraftingInput.of(3, 1, List.of(armor, new ItemStack(Items.GLASS),
                new ItemStack(Items.DIRT))), null);

        ItemStack plain = craft(glass, new ItemStack(Items.DIAMOND_CHESTPLATE), Items.GLASS);
        assert asset(plain).equals("mmu_pack:armor_minified__diamond");
        assert craft(glass, plain, Items.GLASS).get(DataComponents.EQUIPPABLE)
                .equals(Items.DIAMOND_CHESTPLATE.components().get(DataComponents.EQUIPPABLE));

        ItemStack enderite = new ItemStack(Items.NETHERITE_CHESTPLATE);
        CustomData.update(DataComponents.CUSTOM_DATA, enderite,
                tag -> tag.putBoolean(CharmorManager.ENDERITE_MARKER_BOOL, true));
        CharmStackData.setStoredCharms(enderite, List.of(new StoredCharmData(7, 1)));
        CharmorManager.updateArmorTooltip(enderite);
        assert asset(enderite).equals("mmu_pack:test_charm__enderite");
        enderite = craft(glass, enderite, Items.GLASS);
        assert asset(enderite).equals("mmu_pack:test_charm__enderite__minified");
        enderite = craft(pane, enderite, Items.GLASS_PANE);
        assert asset(enderite).equals("mmu_pack:armor_minified__enderite");
        enderite = craft(glass, enderite, Items.GLASS);
        assert asset(enderite).equals("mmu_pack:enderite");

        ItemStack enderiteHelmet = new ItemStack(Items.NETHERITE_HELMET);
        CustomData.update(DataComponents.CUSTOM_DATA, enderiteHelmet,
                tag -> tag.putBoolean(CharmorManager.ENDERITE_MARKER_BOOL, true));
        enderiteHelmet = craft(glass, enderiteHelmet, Items.GLASS);
        assert asset(enderiteHelmet).equals("mmu_pack:armor_minified__enderite");
        enderiteHelmet = craft(glass, enderiteHelmet, Items.GLASS);
        assert asset(enderiteHelmet).equals("mmu_pack:enderite");

        ItemStack minifiedHelmet = craft(glass, new ItemStack(Items.DIAMOND_HELMET), Items.GLASS);
        setSoaring(false);
        CharmorManager.updateArmorTooltip(hiddenMinified);
        assert asset(hiddenMinified).equals("mmu_pack:test_charm__diamond");
        assert hiddenMinified.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 2;
        CharmorManager.updateArmorTooltip(minifiedHelmet);
        assert minifiedHelmet.get(DataComponents.EQUIPPABLE)
                .equals(Items.DIAMOND_HELMET.components().get(DataComponents.EQUIPPABLE));
        assert minifiedHelmet.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty();
        assert !glass.matches(CraftingInput.of(2, 1, List.of(plain, new ItemStack(Items.GLASS))), null);
        System.out.println("Armor appearance checks passed.");
    }

    private static ItemStack craft(ToggleArmorAppearanceRecipe recipe, ItemStack armor, net.minecraft.world.item.Item ingredient) {
        var input = CraftingInput.of(2, 1, List.of(new ItemStack(ingredient), armor));
        assert recipe.matches(input, null);
        return recipe.assemble(input);
    }

    private static String asset(ItemStack stack) {
        return stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().toString();
    }

    private static void setSoaring(boolean enabled) {
        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey(FeatureToggles.SOARING).setEnabled(enabled))
                .build());
    }
}
