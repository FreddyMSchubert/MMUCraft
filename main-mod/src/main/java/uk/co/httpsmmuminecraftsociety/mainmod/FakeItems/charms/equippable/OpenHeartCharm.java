package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.baseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class OpenHeartCharm implements Charm, baseItemChangeCallbackCharm
{
    public static final String OPEN_HEART_CHARM_ID = "cosmetic-charm-open-heart";

    @Override
    public String id()
    {
        return OPEN_HEART_CHARM_ID;
    }

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "open_heart_health", Attributes.MAX_HEALTH, 6.0, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "open_heart_health", Attributes.MAX_HEALTH);
        return stack;
    }
}
