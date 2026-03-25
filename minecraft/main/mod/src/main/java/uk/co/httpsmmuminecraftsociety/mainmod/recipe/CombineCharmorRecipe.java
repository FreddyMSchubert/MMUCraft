package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.Arrays;
import java.util.Optional;

public class CombineCharmorRecipe extends CustomRecipe
{
    private record craftingInfo(boolean craftable, ItemStack armor, ItemStack charm) {}

    private craftingInfo getCraftingInfo(CraftingInput input)
    {
        // must have 1x armor item with a free slot
        ItemStack armor = null;
        // and 1x charm
        ItemStack charm = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);

            if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
                if (armor != null || !CharmorManager.canEquipMoreCharms(stack)) continue;
                armor = stack;
                continue;
            }

            CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
            if (!cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("charm-") && stack.getItem().equals(Items.COMMAND_BLOCK)) {
                if (charm != null) continue;
                charm = stack;
                continue;
            }
        }

        return new craftingInfo(armor != null && charm != null, armor, charm);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return getCraftingInfo(input).craftable && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        craftingInfo cinfo = getCraftingInfo(input);
        if (!cinfo.craftable) return ItemStack.EMPTY;

        ItemStack resultStack = cinfo.armor.copy();

        Optional<int[]> optArmorAbilities = cinfo.armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID);
        Optional<int[]> charmAbilities = cinfo.charm.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID);

        int[] armorAbilities;
        int charmAbility;

        armorAbilities = optArmorAbilities.orElseGet(() -> new int[0]);
        if (charmAbilities.isEmpty()) {
            MainMod.LOGGER.info("Charm had no abilities, this should never happen. Check the recipe input. Charm nbt: " + cinfo.charm.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            return ItemStack.EMPTY;
        } else {
            charmAbility = charmAbilities.get()[0];
        }
        if (Arrays.stream(armorAbilities).anyMatch(a -> a == charmAbility)) {
            return ItemStack.EMPTY;
        }

        // update charm abilities
        int[] newAbilities = new int[armorAbilities.length + 1];
        System.arraycopy(armorAbilities, 0, newAbilities, 0, armorAbilities.length);
        newAbilities[newAbilities.length - 1] = charmAbility;
        CompoundTag newData = resultStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        newData.putIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID, newAbilities);
        resultStack.set(DataComponents.CUSTOM_DATA, CustomData.of(newData));

        // call enable effect callback
        FakeItem fakeItem = FakeItems.CHARM_EFFECT_ID_MAP.get(charmAbility);
        CharmItemFeature cif = fakeItem.getFeature(CharmItemFeature.class);
        if (cif.charm() instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(resultStack);
        }

        // update tooltip
        CharmorManager.updateArmorTooltip(resultStack);

        // update armor rendering
        FakeItem renderedCharm = FakeItems.CHARM_EFFECT_ID_MAP.get(armorAbilities.length > 0 ? armorAbilities[0] : charmAbility);
        EquippableCharmItemFeature eqcif = renderedCharm.getFeature(EquippableCharmItemFeature.class);
        if (eqcif != null) {
            String materialString = getArmorMaterialType(resultStack);

            // remove __charm from end
            String charmResourcePath = eqcif.equippable().assetId().get().identifier().getPath();
            String withoutCharm = charmResourcePath.substring(0, charmResourcePath.indexOf("__charm"));
            String newResourcePath = withoutCharm + "__" + materialString;
            Equippable newEquippableSettings = EquippableCharmItemFeature.createEquippableSettings(newResourcePath, eqcif.equippable().slot());

            resultStack.set(DataComponents.EQUIPPABLE, newEquippableSettings);
        }

        return resultStack;
    }

    private String getArmorMaterialType(ItemStack stack) {
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND)) return "diamond";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE)) return "netherite";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_IRON)) return "iron";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_GOLD)) return "gold";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_COPPER)) return "copper";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_LEATHER)) return "leather";
        if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS_CHAINMAIL)) return "chainmail";
        return "";
    }


    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.COMBINE_CHARMOR_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
