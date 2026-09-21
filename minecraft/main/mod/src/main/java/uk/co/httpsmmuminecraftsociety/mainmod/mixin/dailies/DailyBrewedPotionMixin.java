package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.BrewedPotionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(BrewedPotionTrigger.class)
public abstract class DailyBrewedPotionMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordPotion(ServerPlayer player, PotionContents potion, CallbackInfo ci) {
        potion.potion().flatMap(holder -> holder.unwrapKey()).ifPresent(key -> DailyTaskManager.record(
                player,
                DailyTaskEvent.of(DailyTaskEvent.Type.BREW_POTION, key.identifier().toString())
        ));
    }
}
