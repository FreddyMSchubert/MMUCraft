package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class KangarooBootsCharm implements Charm, BaseItemChangeCallbackCharm {
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        Utils.applyItemAttrModifier(stack, "kangaroo_boots_bounciness", Attributes.BOUNCINESS, charmLevel * 0.2f, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        Utils.applyItemAttrModifier(stack, "kangaroo_boots_fall_damage_reduction", Attributes.FALL_DAMAGE_MULTIPLIER, charmLevel * -0.1f, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) {
        Utils.removeItemAttrModifier(stack, "kangaroo_boots_bounciness", Attributes.BOUNCINESS);
        Utils.removeItemAttrModifier(stack, "kangaroo_boots_fall_damage_reduction", Attributes.FALL_DAMAGE_MULTIPLIER);
    }
}
