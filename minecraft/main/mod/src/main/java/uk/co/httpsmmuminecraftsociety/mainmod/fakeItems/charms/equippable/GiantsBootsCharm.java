package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class GiantsBootsCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "giants_boots_size", Attributes.SCALE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_speed", Attributes.MOVEMENT_SPEED, -0.05, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_step", Attributes.STEP_HEIGHT, 2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_jump", Attributes.JUMP_STRENGTH, 0.2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "giants_boots_attack", Attributes.ATTACK_DAMAGE, -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.FEET);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "giants_boots_size", Attributes.SCALE);
        Utils.removeItemAttrModifier(stack, "giants_boots_speed", Attributes.MOVEMENT_SPEED);
        Utils.removeItemAttrModifier(stack, "giants_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE);
        Utils.removeItemAttrModifier(stack, "giants_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE);
        Utils.removeItemAttrModifier(stack, "giants_boots_step", Attributes.STEP_HEIGHT);
        Utils.removeItemAttrModifier(stack, "giants_boots_jump", Attributes.JUMP_STRENGTH);
        Utils.removeItemAttrModifier(stack, "giants_boots_attack", Attributes.ATTACK_DAMAGE);
    }
}
