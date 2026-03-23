package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class UmbrellaCharm implements Charm, EquippedTickCallbackCharm
{
    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        if (level.getGameTime() % 19 != 0) return stack;

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.SLOW_FALLING,
                20,
                255,
                false,
                false,
                false
        );
        player.addEffect(inst);
        return stack;
    }
}
