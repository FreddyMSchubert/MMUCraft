package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class KeepItemsOnVoidDeath {
    private static final String VOID_DEATH_KEEP_INVENTORY_TAG = "mainmod_void_death_keep_inventory";

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void keepEquipmentOnVoidDeath(ServerLevel serverLevel, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && hasVoidDeathTag(serverPlayer)) {
            ci.cancel();
        }
    }

    private boolean hasVoidDeathTag(ServerPlayer player) {
        boolean removed = player.removeTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        if (removed) {
            player.addTag(VOID_DEATH_KEEP_INVENTORY_TAG);
        }
        return removed;
    }
}
