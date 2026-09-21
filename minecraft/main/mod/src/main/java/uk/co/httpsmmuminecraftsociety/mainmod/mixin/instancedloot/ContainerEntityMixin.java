package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootMenus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerEntity.class)
public interface ContainerEntityMixin {
    @Inject(method = "unpackChestVehicleLootTable", at = @At("HEAD"), cancellable = true)
    private void pil$preventSharedLootGeneration(Player player, CallbackInfo ci) {
        if ((Object) this instanceof MinecartChest && (Object) this instanceof AbstractMinecartContainer minecart && InstancedLootMenus.isLootCandidate(minecart)) {
            InstancedLootMenus.preventVanillaMinecartUnpack(minecart, player);
            ci.cancel();
        }
    }
}
