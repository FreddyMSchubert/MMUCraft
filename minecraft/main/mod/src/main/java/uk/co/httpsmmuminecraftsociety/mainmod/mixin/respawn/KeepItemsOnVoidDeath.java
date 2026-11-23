package uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.bigboss.BigBossManager;

@Mixin(Player.class)
public class KeepItemsOnVoidDeath {
    private static final String VOID_DEATH_KEEP_INVENTORY_TAG = "mainmod_void_death_keep_inventory";

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void keepEquipmentOnVoidDeath(ServerLevel serverLevel, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
                && (hasVoidDeathTag(serverPlayer) || hasTag(serverPlayer, BigBossManager.KEEP_TAG))) {
            ci.cancel();
        }
    }

    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void keepBossExperience(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && hasTag(serverPlayer, BigBossManager.KEEP_TAG))
            cir.setReturnValue(0);
    }

    private boolean hasVoidDeathTag(ServerPlayer player) {
        return hasTag(player, VOID_DEATH_KEEP_INVENTORY_TAG);
    }

    private boolean hasTag(ServerPlayer player, String tag) {
        boolean removed = player.removeTag(tag);
        if (removed) {
            player.addTag(tag);
        }
        return removed;
    }
}
