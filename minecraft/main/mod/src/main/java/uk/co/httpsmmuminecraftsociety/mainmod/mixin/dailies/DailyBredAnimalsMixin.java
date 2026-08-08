package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(BredAnimalsTrigger.class)
public abstract class DailyBredAnimalsMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordBreeding(ServerPlayer player, Animal parent, Animal partner, AgeableMob child, CallbackInfo ci) {
        DailyTaskManager.record(player, DailyTaskEvent.of(
                DailyTaskEvent.Type.BREED_ENTITY,
                BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()).toString()
        ));
    }
}
