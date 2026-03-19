package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class CandleOfTheDeepCharm implements Charm, EquippedTickCallbackCharm
{
    public static final String CANDLE_OF_THE_DEEP_CHARM_ID = "cosmetic-charm-candle-of-the-deep";

    @Override
    public String id()
    {
        return CANDLE_OF_THE_DEEP_CHARM_ID;
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
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
