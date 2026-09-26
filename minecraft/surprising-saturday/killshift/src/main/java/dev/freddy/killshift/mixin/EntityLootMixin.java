package dev.freddy.killshift.mixin;

import dev.freddy.killshift.DropRewards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityLootMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void killshift$captureSourceLoot(ServerLevel level, ItemStack stack, Vec3 offset,
            CallbackInfoReturnable<ItemEntity> callback) {
        if (DropRewards.capture((Entity) (Object) this, stack)) callback.setReturnValue(null);
    }
}
