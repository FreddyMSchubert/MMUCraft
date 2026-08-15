package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class BunnyPajamasCharm implements Charm, BaseItemChangeCallbackCharm, EquippedTickCallbackCharm
{
    private static final int PER_TICK_CARROT_EAT_CHANCE = 43;
    private static final int CARROT_NUTRITION_SHARE_LEVEL = 4;

    private static final double BASE_JUMP_POWER = LivingEntity.BASE_JUMP_POWER;
    private static final double BASE_GRAVITY = LivingEntity.DEFAULT_BASE_GRAVITY;
    private static final double VANILLA_VERTICAL_FRICTION = 0.9800000190734863D;
    private static final double BASE_PLAYER_JUMP_APEX = getJumpApexForTotalJumpPower(BASE_JUMP_POWER);

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH, getJumpStrengthAdditionPerLevel(charmLevel), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
        Utils.applyItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE, getSafeFallDistancePerLevel(charmLevel), AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH);
        Utils.removeItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE);
    }

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        if (level.getRandom().nextInt(PER_TICK_CARROT_EAT_CHANCE) != 0) {
            return;
        }

        if (!player.canEat(false)) {
            return;
        }

        Inventory inventory = player.getInventory();
        List<Integer> carrotSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (invStack.is(Items.CARROT) || invStack.is(Items.GOLDEN_CARROT)) {
                carrotSlots.add(i);
            }
        }

        if (carrotSlots.isEmpty()) {
            return;
        }

        int slot = carrotSlots.get(level.getRandom().nextInt(carrotSlots.size()));
        ItemStack foodStack = inventory.getItem(slot);

        if (foodStack.isEmpty()) {
            return;
        }

        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS,
                0.8F,
                0.9F + level.getRandom().nextFloat() * 0.2F
        );

        ItemStack result;
        if (charmLevel >= CARROT_NUTRITION_SHARE_LEVEL)
            result = foodStack.finishUsingItem(level, player);
        else
            result = foodStack.copyWithCount(foodStack.getCount() - 1);

        if (result != foodStack) {
            inventory.setItem(slot, result);
        }
    }

    private static double getJumpApexForTotalJumpPower(double totalJumpPower)
    {
        double height = 0.0D;
        double velocity = totalJumpPower;

        while (velocity > 0.0D) {
            height += velocity;
            velocity = (velocity - BASE_GRAVITY) * VANILLA_VERTICAL_FRICTION;
        }

        return height;
    }

    private static double solveTotalJumpPowerForTargetApex(double targetHeight)
    {
        if (targetHeight <= 0.0D) {
            return 0.0D;
        }

        double low = 0.0D;
        double high = BASE_JUMP_POWER;

        while (getJumpApexForTotalJumpPower(high) < targetHeight) {
            high *= 2.0D;
        }

        for (int i = 0; i < 80; i++) {
            double mid = (low + high) * 0.5D;

            if (getJumpApexForTotalJumpPower(mid) < targetHeight) {
                low = mid;
            }
            else {
                high = mid;
            }
        }

        return (low + high) * 0.5D;
    }

    private static float getIntendedMaxJumpHeightPerLevel(int level)
    {
        return level + 1.05F;
    }

    private static float getJumpStrengthAdditionPerLevel(int level)
    {
        double targetApex = getIntendedMaxJumpHeightPerLevel(level);

        if (targetApex <= BASE_PLAYER_JUMP_APEX) {
            return 0.0F;
        }

        double requiredTotalJumpPower = solveTotalJumpPowerForTargetApex(targetApex);
        double requiredAdditiveModifier = requiredTotalJumpPower - BASE_JUMP_POWER;

        return (float) requiredAdditiveModifier;
    }

    private static float getSafeFallDistancePerLevel(int level)
    {
        return getIntendedMaxJumpHeightPerLevel(level) * 1.5f;
    }
}
