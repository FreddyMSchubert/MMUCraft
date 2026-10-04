package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
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
        var chestplate = Items.DIAMOND_CHESTPLATE.builtInRegistryHolder();
        var itemRegistry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(itemRegistry, false);
        itemRegistry.bindTags(Map.of(
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS, List.of(chestplate),
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND, List.of(chestplate),
                ModItemTagProvider.SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS, List.of(chestplate)
        ));
        var refreshTags = MappedRegistry.class.getDeclaredMethod("refreshTagsInHolders");
        refreshTags.setAccessible(true);
        refreshTags.invoke(itemRegistry);
        frozen.setBoolean(itemRegistry, true);
        var feature = new EquippableCharmItemFeature(
                EquippableCharmItemFeature.createEquippableSettings("test_charm__charm", EquipmentSlot.CHEST));
        FakeItems.CHARM_ID_MAP = Map.of(7, new FakeItem("Test charm", "test-charm", Rarity.COMMON,
                1, false, false, List.of(), Items.HEART_OF_THE_SEA, List.of(feature)));

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

        setSoaring(true);
        CharmorManager.updateArmorTooltip(armor);
        assert armor.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 4;
        for (var excluded : List.of(Items.DIAMOND_HELMET, Items.CARVED_PUMPKIN)) {
            for (var recipe : List.of(pane, glass)) {
                var ingredient = recipe == pane ? Items.GLASS_PANE : Items.GLASS;
                var input = CraftingInput.of(2, 1, List.of(new ItemStack(excluded), new ItemStack(ingredient)));
                assert !recipe.matches(input, null) && recipe.assemble(input).isEmpty();
            }
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
        setSoaring(false);
        CharmorManager.updateArmorTooltip(hiddenMinified);
        assert asset(hiddenMinified).equals("mmu_pack:test_charm__diamond");
        assert hiddenMinified.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 2;
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
