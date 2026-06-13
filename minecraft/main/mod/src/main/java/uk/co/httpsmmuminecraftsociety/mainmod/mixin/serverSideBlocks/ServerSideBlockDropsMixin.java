package uk.co.httpsmmuminecraftsociety.mainmod.mixin.serverSideBlocks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.serverSideBlocks.ServerSideBlocks;

import java.util.List;

@Mixin(BlockBehaviour.class)
public abstract class ServerSideBlockDropsMixin {
    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void mainmod$dropServerSideBlock(
            BlockState state,
            LootParams.Builder builder,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        if (!ServerSideBlocks.isServerSideBlock(state)) {
            return;
        }

        cir.setReturnValue(List.of(ServerSideBlocks.createDrop(state)));
    }
}
