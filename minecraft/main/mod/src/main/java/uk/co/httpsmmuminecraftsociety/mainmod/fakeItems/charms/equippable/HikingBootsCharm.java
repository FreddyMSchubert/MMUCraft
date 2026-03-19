package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class HikingBootsCharm implements Charm, BaseItemChangeCallbackCharm
{
    public static final String HIKING_BOOTS_CHARM_ID_BEGINNING = "cosmetic-charm-hiking-boots-";
    private static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "hiking_boots_step_id");

    private final int level;

    public HikingBootsCharm(int level) {
        this.level = level;
    }

    @Override
    public String id()
    {
        return HIKING_BOOTS_CHARM_ID_BEGINNING + level;
    }

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "hiking_boots_step", Attributes.STEP_HEIGHT, getStepHeightForLevel(this.level), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET);
        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "hiking_boots_step", Attributes.STEP_HEIGHT);
        return stack;
    }

    private static float getStepHeightForLevel(int level) {
        return switch (level)
        {
            case 0 -> 0.5f;
            case 1 -> 1f;
            case 2 -> 1.5f;
            default -> throw new IllegalStateException("Unexpected hiking boots level: " + level);
        };
    }
}
