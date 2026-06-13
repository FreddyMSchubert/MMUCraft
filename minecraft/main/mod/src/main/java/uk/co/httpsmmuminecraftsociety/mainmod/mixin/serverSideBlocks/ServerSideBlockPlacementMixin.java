package uk.co.httpsmmuminecraftsociety.mainmod.mixin.serverSideBlocks;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.GameMasterBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.serverSideBlocks.ServerSideBlocks;

@Mixin(GameMasterBlockItem.class)
public abstract class ServerSideBlockPlacementMixin {
    @Redirect(
            method = "getPlacementState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;canUseGameMasterBlocks()Z")
    )
    private boolean mainmod$allowServerSideBlockPlacement(Player player) {
        BlockItem self = (BlockItem) (Object) this;
        return player.canUseGameMasterBlocks() || ServerSideBlocks.isServerSideBlock(self.getBlock());
    }
}
