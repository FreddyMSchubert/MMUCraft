package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(Animal.class)
public abstract class DailyAnimalFeedingMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mainmod$recordFeeding(Player player, InteractionHand hand, CallbackInfoReturnable<?> callback) {
        Animal animal = (Animal)(Object)this;
        ItemStack food = player.getItemInHand(hand);
        boolean acceptsFood = animal.isFood(food)
                && ((animal.getAge() == 0 && animal.canFallInLove()) || animal.canAgeUp());
        if (acceptsFood && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.of(
                    DailyTaskEvent.Type.FEED_ENTITY,
                    DailyTargetId.of(animal.getType())
            ));
        }
    }
}
