package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public class HikingBootsCharm implements Charm, BaseItemChangeCallbackCharm, TickCallbackCharm
{
    private static final Identifier ITEM_STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "hiking_boots_step");
    private static final Identifier SPRINT_STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "hiking_boots_sprint_step");

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, ITEM_STEP_ID, Attributes.STEP_HEIGHT);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, ITEM_STEP_ID, Attributes.STEP_HEIGHT);
    }

    @Override
    public void onTick(ServerPlayer player, ServerLevel level)
    {
        AttributeInstance stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight == null) return;
        if (stepHeight.getModifier(ITEM_STEP_ID) != null) stepHeight.removeModifier(ITEM_STEP_ID);

        int charmLevel = CharmsManager.getPlayerCharmLevel(player, HikingBootsCharm.class);
        float boost = player.isSprinting() && charmLevel > 0 ? getStepHeightForLevel(charmLevel) : 0;
        AttributeModifier current = stepHeight.getModifier(SPRINT_STEP_ID);
        if (current != null && current.amount() == boost) return;
        if (current != null) stepHeight.removeModifier(SPRINT_STEP_ID);
        if (boost > 0) stepHeight.addTransientModifier(new AttributeModifier(SPRINT_STEP_ID, boost, AttributeModifier.Operation.ADD_VALUE));
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
