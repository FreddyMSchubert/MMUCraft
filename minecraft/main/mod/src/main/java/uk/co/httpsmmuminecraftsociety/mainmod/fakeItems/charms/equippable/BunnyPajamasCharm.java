package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
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

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.applyItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH, 0.6, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
        Utils.applyItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE, 7, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
        Utils.applyItemAttrModifier(stack, "bunny-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.LEGS);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel)
    {
        Utils.removeItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH);
        Utils.removeItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE);
        Utils.removeItemAttrModifier(stack, "bunny-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER);
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

        ItemStack result = foodStack.finishUsingItem(level, player);
        if (result != foodStack) {
            inventory.setItem(slot, result);
        }
    }
}
