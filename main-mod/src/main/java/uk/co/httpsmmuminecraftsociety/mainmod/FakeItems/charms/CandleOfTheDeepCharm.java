package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class CandleOfTheDeepCharm implements Charm
{
    public static final String CANDLE_OF_THE_DEEP_CHARM_ID = "cosmetic-charm-candle-of-the-deep";

    @Override
    public String id()
    {
        return CANDLE_OF_THE_DEEP_CHARM_ID;
    }

    @Override
    public ItemStack onCreation(ItemStack stack)
    {
        return stack;
    }

    @Override
    public boolean subcribeToOnTick()
    {
        return true;
    }

    @Override
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        if (level.getGameTime() % 15 != 0) return stack;

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                220,
                0,
                false,
                false,
                false
        );
        player.addEffect(inst);
        return stack;
    }
}
