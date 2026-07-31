package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

public class ObamiumPyramidCharm implements Charm, UseCallbackCharm {
    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                SoundSource.RECORDS,
                1.0F,
                1.0F
        );
        return InteractionResult.SUCCESS;
    }
}
