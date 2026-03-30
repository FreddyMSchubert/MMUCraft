package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class LeprechaunBootsCharm implements Charm, EquippedTickCallbackCharm, BaseItemChangeCallbackCharm
{
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_size", Attributes.SCALE, -0.5, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_speed", Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE, -4, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE, -2.5, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_step", Attributes.STEP_HEIGHT, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_jump", Attributes.JUMP_STRENGTH, -0.2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_attack", Attributes.ATTACK_DAMAGE, -1, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_health", Attributes.MAX_HEALTH, -19, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "leprechaun_boots_luck", Attributes.LUCK, 1, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_size", Attributes.SCALE);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_speed", Attributes.MOVEMENT_SPEED);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_interaction_block", Attributes.BLOCK_INTERACTION_RANGE);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_interaction_entity", Attributes.ENTITY_INTERACTION_RANGE);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_step", Attributes.STEP_HEIGHT);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_jump", Attributes.JUMP_STRENGTH);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_attack", Attributes.ATTACK_DAMAGE);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_health", Attributes.MAX_HEALTH);
        Utils.removeItemAttrModifier(stack, "leprechaun_boots_luck", Attributes.LUCK);
    }

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        if (level.getGameTime() % 15 != 0) return;

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.WEAKNESS,
                220,
                0,
                false,
                false,
                false
        );
        player.addEffect(inst);
    }
}
