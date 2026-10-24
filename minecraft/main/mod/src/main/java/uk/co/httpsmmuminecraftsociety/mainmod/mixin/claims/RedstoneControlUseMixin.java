package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin({ButtonBlock.class, LeverBlock.class})
abstract class RedstoneControlUseMixin {
    @WrapMethod(method = "useWithoutItem")
    private InteractionResult mainmod$runAuthorizedControl(BlockState state, Level level, BlockPos pos,
                                                            Player player, BlockHitResult hit,
                                                            Operation<InteractionResult> original) {
        boolean active = ClaimsManager.beginControlActivation(player, level, pos);
        try {
            return original.call(state, level, pos, player, hit);
        } finally {
            if (active) ClaimsManager.endControlActivation();
        }
    }
}
