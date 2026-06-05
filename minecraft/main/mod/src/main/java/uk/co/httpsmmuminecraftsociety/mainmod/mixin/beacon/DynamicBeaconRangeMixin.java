package uk.co.httpsmmuminecraftsociety.mainmod.mixin.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.beacon.DynamicBeaconRange;
import uk.co.httpsmmuminecraftsociety.mainmod.beacon.DynamicBeaconRangeHolder;

@Mixin(BeaconBlockEntity.class)
public abstract class DynamicBeaconRangeMixin implements DynamicBeaconRangeHolder {
    @Unique
    private double mainmod$dynamicBeaconRange = 0.0D;

    @Inject(method = "tick", at = @At("HEAD"))
    private static void mainmod$updateDynamicRangeCache(
            Level level,
            BlockPos pos,
            BlockState state,
            BeaconBlockEntity beacon,
            CallbackInfo ci
    ) {
        if (level.isClientSide() || level.getGameTime() % DynamicBeaconRange.RECALCULATE_INTERVAL_TICKS != 0L) {
            return;
        }
        if (beacon instanceof DynamicBeaconRangeHolder holder) {
            DynamicBeaconRange.recalculate(level, pos, holder);
        }
    }

    @ModifyVariable(method = "applyEffects", at = @At(value = "STORE"), ordinal = 0)
    private static double mainmod$useDynamicRange(
            double vanillaRange,
            Level level,
            BlockPos pos,
            int levels,
            Holder<MobEffect> primaryPower,
            Holder<MobEffect> secondaryPower
    ) {
        if (level.getBlockEntity(pos) instanceof DynamicBeaconRangeHolder holder) {
            double range = holder.mainmod$getDynamicBeaconRange();
            return range > 0.0D ? range : DynamicBeaconRange.recalculate(level, pos, holder);
        }
        return vanillaRange;
    }

    @Override
    public void mainmod$setDynamicBeaconRange(double range) {
        this.mainmod$dynamicBeaconRange = range;
    }

    @Override
    public double mainmod$getDynamicBeaconRange() {
        return this.mainmod$dynamicBeaconRange;
    }
}
