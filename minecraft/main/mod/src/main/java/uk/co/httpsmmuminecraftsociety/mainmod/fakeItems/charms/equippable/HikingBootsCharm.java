package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class HikingBootsCharm implements Charm, BaseItemChangeCallbackCharm
{
    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "hiking_boots_step", Attributes.STEP_HEIGHT, getStepHeightForLevel(charmLevel), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "hiking_boots_step", Attributes.STEP_HEIGHT);
    }

    private static float getStepHeightForLevel(int level) {
        return switch (level)
        {
            case 1 -> 0.5f; // one block
            case 2 -> 1f; // fences
            default -> level - 1.5f; // level 3 = 2 blocks, level 4 = 3 blocks, then 1 block per level
        };
    }
}
