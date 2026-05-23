package uk.co.httpsmmuminecraftsociety.mainmod.mixin.exhaustionCharm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.EnduranceCharm;

@Mixin(Block.class)
public class BlockBreakExhaustionMixin
{
    @Redirect(
            method = "playerDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V")
    )
    private void mainmod$reduceBlockBreakExhaustion(
            Player player,
            float exhaustion,
            Level level,
            Player methodPlayer,
            BlockPos blockPos,
            BlockState blockState,
            BlockEntity blockEntity,
            ItemStack itemStack
    )
    {
        player.causeFoodExhaustion(EnduranceCharm.reduceNonCombatExhaustion(player, exhaustion));
    }
}
