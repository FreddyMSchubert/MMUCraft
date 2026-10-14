package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.ReapingGloveCharm;

@Mixin(LivingEntity.class)
public abstract class ReapingGloveDropMixin {
    @Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
    private void mainmod$multiplyKillXp(ServerLevel level, Entity killer, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Mob mob) || !(killer instanceof ServerPlayer player)) return;
        int base = cir.getReturnValue();
        if (base <= 0) return;
        double boosted = base * ReapingGloveCharm.multiplier(
                CharmsManager.getPlayerCharmLevel(player, ReapingGloveCharm.class));
        int whole = (int) boosted;
        cir.setReturnValue(whole + (mob.getRandom().nextDouble() < boosted - whole ? 1 : 0));
    }
}
