package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.sled.Sleds;

@Mixin(VehicleEntity.class)
public abstract class SledBoatDropMixin {
    @Inject(method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
            at = @At("HEAD"), cancellable = true)
    private void mainmod$dropSled(ServerLevel level, Item item, CallbackInfo ci) {
        if (!((Object) this instanceof Boat boat) || !Sleds.isSled(boat)) return;
        Sleds.destroy(boat, level);
        ci.cancel();
    }
}
