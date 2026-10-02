package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(ServerPlayerGameMode.class)
abstract class PlayerActionContextMixin {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean mainmod$trackBlockBreak(BlockPos pos, Operation<Boolean> original) {
        ClaimsManager.beginPlayerAction(player);
        try {
            return original.call(pos);
        } finally {
            ClaimsManager.endPlayerAction();
        }
    }

    @WrapMethod(method = "useItem")
    private InteractionResult mainmod$trackItemUse(ServerPlayer player, Level level, ItemStack stack,
                                                   InteractionHand hand, Operation<InteractionResult> original) {
        ClaimsManager.beginPlayerAction(player);
        try {
            return original.call(player, level, stack, hand);
        } finally {
            ClaimsManager.endPlayerAction();
        }
    }

    @WrapMethod(method = "useItemOn")
    private InteractionResult mainmod$trackBlockUse(ServerPlayer player, Level level, ItemStack stack,
                                                     InteractionHand hand, BlockHitResult hit,
                                                     Operation<InteractionResult> original) {
        ClaimsManager.beginPlayerAction(player);
        try {
            return original.call(player, level, stack, hand, hit);
        } finally {
            ClaimsManager.endPlayerAction();
        }
    }
}
