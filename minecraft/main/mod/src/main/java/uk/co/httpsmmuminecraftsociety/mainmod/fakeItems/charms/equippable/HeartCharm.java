package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class HeartCharm implements Charm, BaseItemChangeCallbackCharm
{
    public static final String OPEN_HEART_CHARM_ID_BEGINNING = "cosmetic-charm-heart-";

    private final int level;

    public HeartCharm(int level) {
        this.level = level;
    }

    @Override
    public String id()
    {
        return OPEN_HEART_CHARM_ID_BEGINNING + level;
    }

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "open_heart_health", Attributes.MAX_HEALTH, getMaxHeartsForLevel(this.level), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST);
        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "open_heart_health", Attributes.MAX_HEALTH);
        return stack;
    }

    private static float getMaxHeartsForLevel(int level) {
        return switch (level)
        {
            case 0 -> 4f;
            case 1 -> 8f;
            case 2 -> 12f;
            case 3 -> 20f;
            default -> 2f;
        };
    }
}
