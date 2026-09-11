package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

import java.util.Optional;

public final class GliderCharm implements Charm, BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 54;
    private static final ResourceKey<EquipmentAsset> GLIDER_EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "glider")
    );

    public static boolean isGlider(ItemStack stack) {
        return stack.is(Items.ELYTRA) && CharmStackData.getStoredCharms(stack).stream()
                .anyMatch(charm -> charm.charmId() == CHARM_ID);
    }

    public static TriState allowEnchanting(Holder<Enchantment> enchantment, ItemStack stack, EnchantingContext context) {
        if (!isGlider(stack)) return TriState.DEFAULT;
        return TriState.of(enchantment.is(Enchantments.MENDING) || enchantment.is(ModEnchantments.SOULBOUND));
    }

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        Equippable elytraSettings = Items.ELYTRA.components().get(DataComponents.EQUIPPABLE);
        if (elytraSettings == null) return;

        stack.set(DataComponents.EQUIPPABLE, new Equippable(
                elytraSettings.slot(),
                elytraSettings.equipSound(),
                Optional.of(GLIDER_EQUIPMENT_ASSET),
                elytraSettings.cameraOverlay(),
                elytraSettings.allowedEntities(),
                elytraSettings.dispensable(),
                elytraSettings.swappable(),
                elytraSettings.damageOnHurt(),
                elytraSettings.equipOnInteract(),
                elytraSettings.canBeSheared(),
                elytraSettings.shearingSound()
        ));
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) {
        stack.set(DataComponents.EQUIPPABLE, Items.ELYTRA.components().get(DataComponents.EQUIPPABLE));
    }
}
