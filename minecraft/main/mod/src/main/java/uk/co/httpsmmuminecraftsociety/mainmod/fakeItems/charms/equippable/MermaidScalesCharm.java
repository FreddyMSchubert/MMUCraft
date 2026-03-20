package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class MermaidScalesCharm implements Charm, EquippedTickCallbackCharm
{
    public static final String MERMAID_SCALES_CHARM_ID = "cosmetic-charm-mermaid-scales";

    @Override
    public String id()
    {
        return MERMAID_SCALES_CHARM_ID;
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        if (level.getGameTime() % 19 != 0) return stack;

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.DOLPHINS_GRACE,
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
