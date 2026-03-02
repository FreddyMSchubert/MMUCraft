package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public final class RunningShoesCharm implements Charm {
    public static final String FROST_WALKER_CHARM_ID = "cosmetic-charm-running-shoes";

    public static final int CHARGE_GAIN_PER_TICK = 1;
    public static final int CHARGE_DRAIN_PER_TICK = 25;

    public static final int MAX_CHARGE = 123;

    private static final String TAG_CHARGE = "rb_charge";

    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "frost_walker_speed_id");

    @Override
    public String id() {
        return FROST_WALKER_CHARM_ID;
    }

    @Override
    public ItemStack onCreation(ItemStack stack) {
        return stack;
    }

    @Override
    public boolean subcribeToOnTick() {
        return true;
    }

    @Override
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level) {
        Utils.removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);

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
            Utils.applyModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, charge / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        return stack;
    }

    @Override
    public ItemStack onEquipmentSlotChange(ServerPlayer player, ItemStack stack, int from, int to)
    {
        return null;
    }
}
