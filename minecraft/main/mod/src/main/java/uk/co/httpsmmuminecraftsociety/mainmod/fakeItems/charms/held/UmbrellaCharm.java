package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;

public class UmbrellaCharm implements Charm, EquippedTickCallbackCharm
{
    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        if (level.getGameTime() % 19 != 0) return;

        MasteryAdvancements.grant(player, "utility/use_staff");
        MasteryAdvancements.grant(player, "utility/staff_brolly");
        MasteryAdvancements.grantIfAll(player, "utility/all_staves",
                "utility/staff_crafting", "utility/staff_ender_chest", "utility/staff_brolly");

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.SLOW_FALLING,
                20,
                255,
                false,
                false,
                false
        );
        player.addEffect(inst);
    }
}
