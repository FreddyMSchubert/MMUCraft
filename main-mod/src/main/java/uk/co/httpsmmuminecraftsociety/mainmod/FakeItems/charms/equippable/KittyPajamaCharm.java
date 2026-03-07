package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class KittyPajamaCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-kitty-pajamas";
    }

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "kitty-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER, -1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.LEGS);

        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "kitty-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER);

        return stack;
    }
}
