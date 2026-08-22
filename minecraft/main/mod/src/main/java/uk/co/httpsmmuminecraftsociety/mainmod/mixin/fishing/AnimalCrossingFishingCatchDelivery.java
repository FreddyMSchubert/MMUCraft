package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingCatches;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingJumpScares;

import java.util.List;

final class AnimalCrossingFishingCatchDelivery {
    private AnimalCrossingFishingCatchDelivery() {}

    static void deliver(
            ServerLevel level,
            FishingHook hook,
            ServerPlayer player,
            ItemStack fishingRod,
            ItemStack catchResult
    ) {
        if (FishingJumpScares.shouldTrigger(hook.getRandom())) {
            FishingJumpScares.spawn(level, hook, player);
            return;
        }

        ItemStack result = FishingCatches.claimDrop(player, catchResult, hook.getRandom());
        CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, fishingRod, hook, List.of(result));
        FishingCatches.catchMessage(result).ifPresent(player::sendOverlayMessage);
        FishingCatches.trackCatch(player, result);

        ItemEntity itemEntity = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), result.copy());
        double dx = player.getX() - hook.getX();
        double dy = player.getY() - hook.getY();
        double dz = player.getZ() - hook.getZ();
        itemEntity.setDeltaMovement(
                dx * 0.1D,
                dy * 0.1D + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08D,
                dz * 0.1D
        );
        level.addFreshEntity(itemEntity);
        level.addFreshEntity(new ExperienceOrb(
                level,
                player.getX(),
                player.getY() + 0.5D,
                player.getZ() + 0.5D,
                hook.getRandom().nextInt(6) + 1
        ));
        player.awardStat(Stats.FISH_CAUGHT, 1);
    }
}
