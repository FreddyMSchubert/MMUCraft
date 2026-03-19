package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public final class RunningShoesCharm implements Charm, TickCallbackCharm, EquippedTickCallbackCharm
{
    public static final String RUNNING_SHOES_CHARM_ID = "cosmetic-charm-running-shoes";

    public static final int CHARGE_GAIN_PER_TICK = 1;
    public static final int CHARGE_DRAIN_PER_TICK = 25;

    public static final int MAX_CHARGE = 123;

    private static final String TAG_CHARGE = "rb_charge";

    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "running_shoes_speed_id");

    @Override
    public String id() {
        return RUNNING_SHOES_CHARM_ID;
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();

        int charge = tag.getIntOr(TAG_CHARGE, 0);

        boolean charging =
                player.isSprinting()
                        && !player.isCrouching()
                        && !player.isInLiquid()
                        && !player.isJumping();

        if (charging) {
            if (charge < MAX_CHARGE)
                charge += CHARGE_GAIN_PER_TICK;
        } else if (charge > 0)
            charge -= CHARGE_DRAIN_PER_TICK;

        tag.putInt(TAG_CHARGE, charge);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        if (charge > 0) {
            // account for no overlap with leprechaun boots charm, which also modifies movement speed
            double normalBaseSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            double intendedSpeed = Math.max(normalBaseSpeed * 1.3 /*sprint speed*/, normalBaseSpeed + (charge / 100.0 * normalBaseSpeed));
            Utils.applyPlayerModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, intendedSpeed - player.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), AttributeModifier.Operation.ADD_VALUE);
        }

        return stack;
    }

    @Override
    public void onTick(ServerPlayer player, ServerLevel level)
    {
        Utils.removePlayerModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
    }
}
