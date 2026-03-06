package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.List;

public class CombineCosmeticRecipe implements CraftingRecipe
{
    private final int trashVal; // dummy field because minecraft forces their recipes data driven. well data drive this important value.

    public CombineCosmeticRecipe(int trashVal) {
        this.trashVal = trashVal;
    }

    private record craftingInfo(boolean craftable, ItemStack armor, ItemStack cosmetic) {}

    private craftingInfo getCraftingInfo(CraftingInput input)
    {
        // must have 1x armor item with a free slot
        ItemStack armor = null;
        // and 1x cosmetic
        ItemStack cosmetic = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);

            if (stack.is(ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS)) {
                if (armor != null || !stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).strings().isEmpty()) continue;
                armor = stack;
                continue;
            }

            CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
            if (!cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("cosmetic-hat-") && stack.getItem().equals(Items.CARVED_PUMPKIN)) {
                if (cosmetic != null) continue;
                cosmetic = stack;
                continue;
            }
        }

        return new craftingInfo(armor != null && cosmetic != null, armor, cosmetic);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return getCraftingInfo(input).craftable && input.ingredientCount() == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider)
    {
        craftingInfo cinfo = getCraftingInfo(input);

        CustomModelData armorCmd = cinfo.armor.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (!armorCmd.strings().isEmpty())
            return ItemStack.EMPTY;

        CustomModelData cosmeticCmd = cinfo.cosmetic.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cosmeticCmd.strings().isEmpty() || cosmeticCmd.strings().getFirst().isEmpty()) return ItemStack.EMPTY;
        String cosmeticPath = cosmeticCmd.strings().getFirst();

        Equippable defaultEquippable = cinfo.armor.getItem().getDefaultInstance().get(DataComponents.EQUIPPABLE);
        Equippable.Builder newEquippableBuilder = Equippable.builder(defaultEquippable.slot())
                .setEquipSound(defaultEquippable.equipSound())
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, cosmeticPath)))
                .setDispensable(defaultEquippable.dispensable())
                .setSwappable(defaultEquippable.swappable())
                .setDamageOnHurt(defaultEquippable.damageOnHurt())
                .setEquipOnInteract(defaultEquippable.equipOnInteract())
                .setCanBeSheared(defaultEquippable.canBeSheared())
                .setShearingSound(defaultEquippable.shearingSound());
        if (defaultEquippable.cameraOverlay().isPresent())
            newEquippableBuilder.setCameraOverlay(defaultEquippable.cameraOverlay().get());
        if (defaultEquippable.allowedEntities().isPresent())
            newEquippableBuilder.setAllowedEntities(defaultEquippable.allowedEntities().get());
        Equippable newEquippable = newEquippableBuilder.build();

        ItemStack returnStack = cinfo.armor.copy();
        returnStack.set(DataComponents.EQUIPPABLE, newEquippable);

        return returnStack;
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer()
    {
        return MainModRecipes.COMBINE_CHARMOR_SERIALIZER;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
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

    public static final class Serializer implements RecipeSerializer<CombineCosmeticRecipe> {

        public static final MapCodec<CombineCosmeticRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("trashVal").forGetter(r -> r.trashVal)
                ).apply(instance, CombineCosmeticRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CombineCosmeticRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, r -> r.trashVal,
                        CombineCosmeticRecipe::new
                );

        @Override
        public MapCodec<CombineCosmeticRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CombineCosmeticRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
