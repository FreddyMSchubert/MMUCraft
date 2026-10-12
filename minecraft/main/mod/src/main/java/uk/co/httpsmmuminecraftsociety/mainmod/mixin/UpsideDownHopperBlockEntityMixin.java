package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHopper;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.UpsideDownHoppers;

@Mixin(HopperBlockEntity.class)
public abstract class UpsideDownHopperBlockEntityMixin implements UpsideDownHopper {
    @Unique private static final String MAINMOD$UPSIDE_DOWN_KEY = "mainmod_upside_down";
    @Unique private boolean mainmod$upsideDown;

    @Override
    public boolean mainmod$isUpsideDown() {
        return mainmod$upsideDown;
    }

    @Override
    public void mainmod$setUpsideDown(boolean upsideDown) {
        mainmod$upsideDown = upsideDown;
        ((HopperBlockEntity) (Object) this).setChanged();
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mainmod$loadUpsideDown(ValueInput input, CallbackInfo ci) {
        mainmod$upsideDown = input.getBooleanOr(MAINMOD$UPSIDE_DOWN_KEY, false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mainmod$saveUpsideDown(ValueOutput output, CallbackInfo ci) {
        if (mainmod$upsideDown) output.putBoolean(MAINMOD$UPSIDE_DOWN_KEY, true);
    }

    @ModifyExpressionValue(
            method = {"ejectItems", "getAttachedContainer"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;facing:Lnet/minecraft/core/Direction;")
    )
    private static Direction mainmod$upwardOutput(Direction original, Level level, BlockPos pos, HopperBlockEntity hopper) {
        return ((UpsideDownHopper) hopper).mainmod$isUpsideDown() ? Direction.UP : original;
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private static void mainmod$skipTopPickup(Level level, BlockPos pos,
                                               net.minecraft.world.level.block.state.BlockState state,
                                               Entity entity, HopperBlockEntity hopper, CallbackInfo ci) {
        if (((UpsideDownHopper) hopper).mainmod$isUpsideDown()) ci.cancel();
    }

    @Inject(method = "pushItemsTick", at = @At("HEAD"))
    private static void mainmod$repairMarker(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                              HopperBlockEntity hopper, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && ((UpsideDownHopper) hopper).mainmod$isUpsideDown()
                && (level.getGameTime() + pos.asLong()) % 200 == 0) {
            UpsideDownHoppers.ensureMarker(serverLevel, pos);
        }
    }
}
