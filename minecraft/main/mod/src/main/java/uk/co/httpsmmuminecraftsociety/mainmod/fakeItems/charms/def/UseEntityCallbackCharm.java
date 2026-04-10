package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

public interface UseEntityCallbackCharm extends Charm
{
    InteractionResult onUseEntity(ItemStack stack, Player player, Level level, InteractionHand interactionHand, Entity entity, @Nullable EntityHitResult entityHitResult, int charmLevel);
}
