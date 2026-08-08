package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.CuredZombieVillagerTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(CuredZombieVillagerTrigger.class)
public abstract class DailyCuredZombieVillagerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordCure(ServerPlayer player, Zombie zombie, Villager villager, CallbackInfo ci) {
        DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.CURE_ZOMBIE_VILLAGER));
    }
}
