package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

public interface AttackEntityCallbackCharm extends Charm {
    InteractionResult onAttackEntity(ItemStack stack, ServerPlayer player, ServerLevel level,
                                     InteractionHand hand, Entity entity, @Nullable EntityHitResult hit,
                                     int charmLevel);
}
