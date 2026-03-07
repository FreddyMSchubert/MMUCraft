package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class BunnyPajamaCharm implements Charm, BaseItemChangeCallbackCharm, EquippedTickCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-bunny-pajama";
    }

    private static final int PER_TICK_CARROT_EAT_CHANCE = 6000;

    @Override
    public @NotNull ItemStack enableEffectForItem(ItemStack stack)
    {
        stack = Utils.applyItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH, 0.6, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
        stack = Utils.applyItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE, 7, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS);
        stack = Utils.applyItemAttrModifier(stack, "bunny-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, EquipmentSlotGroup.LEGS);

        return stack;
    }

    @Override
    public @NotNull ItemStack disableEffectForItem(ItemStack stack)
    {
        stack = Utils.removeItemAttrModifier(stack, "bunny-pajama-jump-boost", Attributes.JUMP_STRENGTH);
        stack = Utils.removeItemAttrModifier(stack, "bunny-pajama-safe-fall-distance", Attributes.SAFE_FALL_DISTANCE);
        stack = Utils.removeItemAttrModifier(stack, "bunny-pajama-fall-damage", Attributes.FALL_DAMAGE_MULTIPLIER);

        return stack;
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        if (Math.floor(Math.random() * 6000) != 42) return stack;

        // player.getInventory().for

        return stack;
    }
}
