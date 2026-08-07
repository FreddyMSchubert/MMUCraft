package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseEntityCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

public class InvisiCarrotCharm implements Charm, UseEntityCallbackCharm
{
    @Override
    public InteractionResult onUseEntity(ItemStack stack, Player player, Level level, InteractionHand interactionHand, Entity entity, @Nullable EntityHitResult entityHitResult, int charmLevel)
    {
        if (!(entity instanceof ItemFrame frame)) {
            return InteractionResult.PASS;
        }

        if (frame.isInvisible()) {
            return InteractionResult.PASS;
        }

        frame.setInvisible(true);

        stack.shrink(1);

        if (player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.MODIFY_ITEM_FRAME));
        }

        return InteractionResult.SUCCESS_SERVER;
    }
}
