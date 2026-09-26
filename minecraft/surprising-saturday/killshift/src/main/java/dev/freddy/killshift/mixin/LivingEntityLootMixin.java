package dev.freddy.killshift.mixin;

import dev.freddy.killshift.DropRewards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityLootMixin {
    @Shadow
    protected abstract void dropEquipment(ServerLevel level);

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void killshift$beginSourceLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        DropRewards.begin((LivingEntity) (Object) this, source);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void killshift$endSourceLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        DropRewards.end((LivingEntity) (Object) this);
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void killshift$transferSourceEquipment(LivingEntity dead, ServerLevel level) {
        if (!DropRewards.transferEquipment(dead)) dropEquipment(level);
    }

    @Redirect(method = "dropExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void killshift$awardSourceExperience(ServerLevel level, Vec3 position, int amount) {
        DropRewards.awardExperience((LivingEntity) (Object) this, level, position, amount);
    }
}
