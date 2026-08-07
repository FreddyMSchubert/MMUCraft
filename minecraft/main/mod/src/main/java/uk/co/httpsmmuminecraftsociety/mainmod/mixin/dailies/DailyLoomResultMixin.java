package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

@Mixin(targets = "net.minecraft.world.inventory.LoomMenu$6")
public abstract class DailyLoomResultMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void mainmod$recordBannerPattern(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.CUSTOMIZE_BANNER));
        }
    }
}
