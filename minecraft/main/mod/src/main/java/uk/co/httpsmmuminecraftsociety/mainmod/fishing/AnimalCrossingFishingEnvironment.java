package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;

public final class AnimalCrossingFishingEnvironment {
    private static final double SCURRY_TRIGGER_DISTANCE = 1.15D;

    private AnimalCrossingFishingEnvironment() {}

    public static boolean isStillInWater(ServerLevel level, FishingHook hook) {
        return level.getFluidState(hook.blockPosition()).is(FluidTags.WATER);
    }

    public static int weatherWaitSpeed(ServerLevel level, BlockPos bobberBlockPos, FishingHook hook) {
        int speed = 1;
        BlockPos above = bobberBlockPos.above();
        if (hook.getRandom().nextFloat() < 0.25F && level.isRainingAt(above)) speed++;
        if (hook.getRandom().nextFloat() < 0.5F && !level.canSeeSky(above)) speed--;
        return Math.max(1, speed);
    }

    public static boolean hasNearbyThreat(
            ServerLevel level,
            FishingHook hook,
            Display.ItemDisplay fishShadow
    ) {
        AABB box = fishShadow.getBoundingBox().inflate(
                SCURRY_TRIGGER_DISTANCE,
                0.85D,
                SCURRY_TRIGGER_DISTANCE
        );
        return !level.getEntities(
                hook,
                box,
                entity -> entity.isAlive()
                        && entity != hook.getOwner()
                        && entity != fishShadow
                        && entity.distanceToSqr(fishShadow)
                        <= SCURRY_TRIGGER_DISTANCE * SCURRY_TRIGGER_DISTANCE
        ).isEmpty();
    }
}
