package dev.freddy.chunklocked.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raids.class)
public abstract class MaxRaidOmenMixin {
    @Inject(method = "createOrExtendRaid", at = @At("RETURN"))
    private void chunklocked$maxRaidOmen(ServerPlayer player, BlockPos pos,
            CallbackInfoReturnable<Raid> result) {
        Raid raid = result.getReturnValue();
        if (raid != null) raid.setRaidOmenLevel(raid.getMaxRaidOmenLevel());
    }
}
