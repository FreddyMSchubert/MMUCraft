package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class ExtendoGripCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        Utils.applyItemAttrModifier(stack, "extendo_grip_block_interaction_range", Attributes.BLOCK_INTERACTION_RANGE, 1.999, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        Utils.removeItemAttrModifier(stack, "extendo_grip_block_interaction_range", Attributes.BLOCK_INTERACTION_RANGE);

        return stack;
    }
}
