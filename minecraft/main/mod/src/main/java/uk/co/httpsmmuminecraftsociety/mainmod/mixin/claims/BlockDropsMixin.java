package uk.co.httpsmmuminecraftsociety.mainmod.mixin.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

@Mixin(Block.class)
abstract class BlockDropsMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private static void mainmod$guardSimpleDrops(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!ClaimsManager.allowBlockRemoval(level, pos)) ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"), cancellable = true)
    private static void mainmod$guardAccessorDrops(BlockState state, LevelAccessor accessor, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
        if (accessor instanceof Level level && !ClaimsManager.allowBlockRemoval(level, pos)) ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void mainmod$guardEntityDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!ClaimsManager.allowBlockRemoval(level, pos)) ci.cancel();
    }

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void mainmod$guardPoppedResource(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (!ClaimsManager.allowBlockRemoval(level, pos)) ci.cancel();
    }

    @Inject(method = "popResourceFromFace", at = @At("HEAD"), cancellable = true)
    private static void mainmod$guardPoppedFaceResource(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
        if (!ClaimsManager.allowBlockRemoval(level, pos)) ci.cancel();
    }
}
