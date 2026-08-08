package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(Player.class)
public abstract class DailyExperienceMixin {
    @Unique private int mainmod$levelBeforeExperience;

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void mainmod$captureLevel(int amount, CallbackInfo ci) {
        if ((Object)this instanceof Player player) mainmod$levelBeforeExperience = player.experienceLevel;
    }

    @Inject(method = "giveExperiencePoints", at = @At("RETURN"))
    private void mainmod$recordLevels(int amount, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player && player.experienceLevel > mainmod$levelBeforeExperience) {
            DailyTaskManager.record(player, new DailyTaskEvent(
                    DailyTaskEvent.Type.GAIN_LEVEL,
                    "",
                    "",
                    player.experienceLevel - mainmod$levelBeforeExperience
            ));
        }
    }
}
