package dev.freddy.chunklocked.mixin;

import dev.freddy.chunklocked.ChunkLocked;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class DeathDropMixin {
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void chunklocked$neatDeathPile(ItemStack stack, boolean randomly, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> result) {
        if ((Object) this instanceof ServerPlayer player && player.isDeadOrDying()
                && result.getReturnValue() != null) {
            ChunkLocked.placeDeathDrop(player, result.getReturnValue());
        }
    }
}
