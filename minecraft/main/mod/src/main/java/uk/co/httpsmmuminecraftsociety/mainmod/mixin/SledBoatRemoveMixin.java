package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.sled.Sleds;

@Mixin(AbstractBoat.class)
public abstract class SledBoatRemoveMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void mainmod$removeSledDisplay(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof Boat boat) || boat.level().isClientSide()
                || !reason.shouldDestroy() || !Sleds.isSled(boat)) return;
        Sleds.discardDisplay(boat);
    }
}
