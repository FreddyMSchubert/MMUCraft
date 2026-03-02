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
    @Override
    public String id()
    {
        return "cosmetic-charm-candle-of-the-deep";
    }

    @Override
    public ItemStack onCreation(ItemStack stack)
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("charm-ontickcallback", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
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
