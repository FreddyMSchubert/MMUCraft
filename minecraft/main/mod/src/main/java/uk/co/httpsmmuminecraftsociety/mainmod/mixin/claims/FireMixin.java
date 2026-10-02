package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(FireBlock.class)
abstract class FireMixin {
    @WrapMethod(method = "tick")
    private void mainmod$trackFireTick(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, Operation<Void> original) {
        ClaimsManager.beginFireTick();
        try {
            original.call(state, level, pos, random);
        } finally {
            ClaimsManager.endFireTick();
        }
    }

    @WrapMethod(method = "checkBurnOut")
    private void mainmod$trackBurnOut(Level level, BlockPos pos, int chance,
                                      RandomSource random, int age, Operation<Void> original) {
        ClaimsManager.beginFireTick();
        try {
            original.call(level, pos, chance, random, age);
        } finally {
            ClaimsManager.endFireTick();
        }
    }
}
