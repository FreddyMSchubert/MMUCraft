package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(Mob.class)
public abstract class DailyAnimalFeedingMixin {
    @Inject(method = "usePlayerItem", at = @At("HEAD"))
    private void mainmod$recordFeeding(Player player, InteractionHand hand, ItemStack item, CallbackInfo callback) {
        if ((Object)this instanceof Animal animal
                && animal.isFood(item)
                && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.of(
                    DailyTaskEvent.Type.FEED_ENTITY,
                    DailyTargetId.of(animal.getType())
            ));
        }
    }
}
