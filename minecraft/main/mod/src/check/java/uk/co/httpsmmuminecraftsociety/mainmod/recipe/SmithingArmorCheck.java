package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureToggle;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.Map;

public final class SmithingArmorCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());
        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey(FeatureToggles.SOARING).setEnabled(true))
                .build());
        var diamond = BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND_CHESTPLATE);
        var netherite = BuiltInRegistries.ITEM.wrapAsHolder(Items.NETHERITE_CHESTPLATE);
        ((MappedRegistry<Item>) BuiltInRegistries.ITEM).prepareTagReload(new TagLoader.LoadResult<>(
                Registries.ITEM, Map.of(
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS, List.of(diamond, netherite),
                ModItemTagProvider.DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS, List.of(diamond),
                ModItemTagProvider.TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS, List.of(netherite),
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND, List.of(diamond),
                ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE, List.of(netherite)))).apply();
        FakeItems.CHARM_ID_MAP = Map.of(9000, new FakeItem("Test Charm", "test-charm", Rarity.UNCOMMON,
                "uncommon", 1, false, false, "", List.of(), Items.ELYTRA,
                List.of(new EquippableCharmItemFeature(
                        EquippableCharmItemFeature.createEquippableSettings("heart_sleeve__charm", EquipmentSlot.CHEST)))));

        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        CharmStackData.setStoredCharms(armor, List.of(new StoredCharmData(9000, 1)));
        CustomData.update(DataComponents.CUSTOM_DATA, armor,
                tag -> tag.putBoolean(CharmorManager.ARMOR_MINIFIED_BOOL, true));
        var boost = Holder.Reference.createStandAlone(new HolderOwner<Enchantment>() {},
                ModEnchantments.CHARM_BOOST);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(boost, 1);
        armor.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        CharmorManager.updateArmorTooltip(armor);
        assert CharmorManager.calcCharmSlotCount(armor) == 3;
        assert armor.get(DataComponents.LORE).lines().size() == 6;
        assert armor.get(DataComponents.LORE).lines().get(1).getString().equals("1: Test Charm");
        assert armor.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("heart_sleeve__diamond__minified");

        ItemStack upgraded = TransmuteRecipe.createWithOriginalComponents(
                new ItemStackTemplate(Items.NETHERITE_CHESTPLATE), armor);
        assert upgraded.getEnchantments().getLevel(boost) == 1;
        CharmorManager.updateArmorTooltip(upgraded);
        assert CharmorManager.calcCharmSlotCount(upgraded) == 4;
        assert upgraded.get(DataComponents.LORE).lines().get(4).getString().equals("4: -");
        assert upgraded.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("heart_sleeve__netherite__minified");
        assert CharmorManager.armorMinified(upgraded);

        ItemStack template = fakeIngredient("enderite-upgrade-smithing-template");
        ItemStack ingot = fakeIngredient("enderite-ingot");
        ItemStack enderite = new EnderiteSmithingRecipe().assemble(
                new SmithingRecipeInput(template, upgraded, ingot));
        assert enderite.getEnchantments().getLevel(boost) == 1;
        assert CharmorManager.isEnderite(enderite);
        assert CharmorManager.armorMinified(enderite);
        assert CharmorManager.calcCharmSlotCount(enderite) == 6;
        assert enderite.get(DataComponents.LORE).lines().get(6).getString().equals("6: -");
        assert enderite.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("heart_sleeve__enderite__minified");

        var hiding = new ToggleArmorAppearanceRecipe(ToggleArmorAppearanceRecipe.Target.HIDING);
        CraftingInput input = hidingInput(enderite);
        assert hiding.matches(input, null);
        assert !hiding.matches(CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.GLASS), new ItemStack(Items.TINTED_GLASS), new ItemStack(Items.TINTED_GLASS),
                new ItemStack(Items.TINTED_GLASS), enderite, new ItemStack(Items.TINTED_GLASS),
                new ItemStack(Items.TINTED_GLASS), new ItemStack(Items.TINTED_GLASS), new ItemStack(Items.TINTED_GLASS))), null);
        ItemStack hidden = hiding.assemble(input);
        assert CharmorManager.armorHidden(hidden);
        assert CharmorManager.armorMinified(hidden);
        assert hidden.get(DataComponents.LORE).lines().getLast().getString().equals("Armor: Hidden");
        assert hidden.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("heart_sleeve__charm");

        ItemStack bare = hiding.assemble(hidingInput(new ItemStack(Items.DIAMOND_CHESTPLATE)));
        assert bare.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("armor_hidden");
        assert bare.get(DataComponents.LORE).lines().getLast().getString().equals("Armor: Hidden");

        ItemStack shown = hiding.assemble(hidingInput(hidden));
        assert !CharmorManager.armorHidden(shown);
        assert shown.get(DataComponents.LORE).lines().getLast().getString().equals("Armor: Minified");
        assert shown.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().getPath()
                .equals("heart_sleeve__enderite__minified");
        System.out.println("Smithing armor checks passed.");
    }

    private static CraftingInput hidingInput(ItemStack armor) {
        ItemStack glass = new ItemStack(Items.TINTED_GLASS);
        return CraftingInput.of(3, 3, List.of(
                glass, glass, glass,
                glass, armor, glass,
                glass, glass, glass));
    }

    private static ItemStack fakeIngredient(String id) {
        ItemStack stack = new ItemStack(Items.HEART_OF_THE_SEA);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(id), List.of()));
        return stack;
    }
}
