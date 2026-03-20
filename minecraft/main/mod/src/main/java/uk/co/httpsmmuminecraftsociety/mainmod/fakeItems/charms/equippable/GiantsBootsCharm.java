package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class GiantsBootsCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-giants-boots";
    }

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_size", Attributes.SCALE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_speed", Attributes.MOVEMENT_SPEED, -0.05, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE, 2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_step", Attributes.STEP_HEIGHT, 2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_jump", Attributes.JUMP_STRENGTH, 0.2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        stack = Utils.applyItemAttrModifier(stack, "giants_boots_attack", Attributes.ATTACK_DAMAGE, -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.FEET);

        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_size", Attributes.SCALE);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_speed", Attributes.MOVEMENT_SPEED);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_step", Attributes.STEP_HEIGHT);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_jump", Attributes.JUMP_STRENGTH);
        stack = Utils.removeItemAttrModifier(stack, "giants_boots_attack", Attributes.ATTACK_DAMAGE);

        return stack;
    }
}
