package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.KittyPajamasCharm;

@Mixin(Creeper.class)
public abstract class KittyPajamaScareCreeper extends Monster
{
    protected KittyPajamaScareCreeper(EntityType<? extends @NotNull KittyPajamaScareCreeper> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(at = @At("TAIL"), method = "registerGoals")
    private void addGoals(CallbackInfo info) {
        Goal goal = new AvoidEntityGoal<>(this, Player.class, this::shouldFleeFromPlayer, 6.0F, 1.0D, 1.2D, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
        this.goalSelector.addGoal(1, goal);
    }

    @Inject(at = @At("HEAD"), method = "setTarget", cancellable = true)
    private void avoidTargetingCharmWearer(LivingEntity target, CallbackInfo info) {
        if (shouldFleeFromPlayer(target)) info.cancel();
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void stopTargetingNewCharmWearer(CallbackInfo info) {
        if (this.level().isClientSide()) return;
        if (!shouldFleeFromPlayer(this.getTarget())) return;
        this.setTarget(null);
        Creeper creeper = (Creeper) (Object) this;
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
    }

    @Unique
    private boolean shouldFleeFromPlayer(LivingEntity livingEntity)
    {
        return livingEntity instanceof ServerPlayer player
                && CharmsManager.getPlayerCharmLevel(player, KittyPajamasCharm.class) > 0;
    }
}
