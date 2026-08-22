package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingCatches;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

@Mixin(FishingHook.class)
public abstract class AnimalCrossingFishingHookMixin {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_BITING;
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow private float fishAngle;
    @Shadow @Final private int luck;
    @Shadow @Final private int lureSpeed;

	@Unique private static final int MAX_FISH_SHADOW_AGE = 20 * 65;

    @Unique private AnimalCrossingFishingPhase mainmod$phase = AnimalCrossingFishingPhase.WAITING;
    @Unique private ItemStack mainmod$catchResult;
    @Unique private FishingPersonality mainmod$catchPersonality;
    @Unique private Display.ItemDisplay mainmod$fishShadow;
    @Unique private double mainmod$fishDistance;
    @Unique private double mainmod$targetDistance;
    @Unique private double mainmod$orbitDegrees;
    @Unique private double mainmod$orbitDrift;
    @Unique private int mainmod$selectedBounces;
    @Unique private int mainmod$completedBounces;
    @Unique private int mainmod$animationTicks;
    @Unique private int mainmod$pauseTicks;
    @Unique private int mainmod$shadowAge;
    @Unique private int mainmod$arrivalTicks;
    @Unique private int mainmod$movementTicksRemaining;
    @Unique private int mainmod$bobberBopRecoveryTicks;
    @Unique private int mainmod$catchAnimationTicks;
    @Unique private ItemStack mainmod$catchingRod = ItemStack.EMPTY;
    @Unique private double mainmod$itemChance = FishingModifiers.DEFAULT_ITEM_CHANCE;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void mainmod$applyCastModifier(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        if (!level.isClientSide()) {
            this.mainmod$itemChance = FishingModifiers.onCast(player);
        }
    }

    @Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
    private void mainmod$runAnimalCrossingFishing(BlockPos bobberBlockPos, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!(hook.level() instanceof ServerLevel level)) {
            return;
        }

        ci.cancel();

		if (!AnimalCrossingFishingEnvironment.isStillInWater(level, hook)) {
            mainmod$cleanupFishShadow();
            mainmod$phase = AnimalCrossingFishingPhase.WAITING;
            return;
        }

		int weatherSpeed = AnimalCrossingFishingEnvironment.weatherWaitSpeed(
				level,
				bobberBlockPos,
				hook
		);
        if (mainmod$phase == AnimalCrossingFishingPhase.CATCH_ANIMATING) {
            mainmod$tickCatchAnimation(level, hook);
        } else if (this.nibble > 0) {
            mainmod$tickBite(level, hook);
        } else if (mainmod$phase == AnimalCrossingFishingPhase.WAITING) {
            mainmod$tickWaiting(level, hook, weatherSpeed);
        } else {
            if (mainmod$tickSafetyRemoval(level, hook)) {
                return;
            }
            mainmod$tickFish(level, hook);
        }
    }

    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void mainmod$onRetrieve(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) {
            return;
        }

        if (mainmod$phase == AnimalCrossingFishingPhase.CATCH_ANIMATING) {
            cir.setReturnValue(0);
            return;
        }

        if (this.nibble > 0 && hook.getPlayerOwner() instanceof ServerPlayer) {
            mainmod$startCatchAnimation(hook, stack);
            cir.setReturnValue(1);
            return;
        }

        mainmod$cleanupFishShadow();
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void mainmod$cleanupWhenRemoved(CallbackInfo ci) {
        mainmod$cleanupFishShadow();
    }

    @Unique
    private void mainmod$tickWaiting(ServerLevel level, FishingHook hook, int weatherSpeed) {
        if (this.timeUntilLured <= 0) {
			this.timeUntilLured = AnimalCrossingFishingTiming.initialWaitTicks(
					hook.getRandom(),
					this.lureSpeed
			);
            this.timeUntilHooked = 0;
        }

        this.timeUntilLured -= weatherSpeed;
        if (this.timeUntilLured > 0) {
            return;
        }

        mainmod$spawnFish(level, hook);
    }

    @Unique
    private void mainmod$spawnFish(ServerLevel level, FishingHook hook) {
		Pair<ItemStack, FishingPersonality> fish = FishingCatches.random(
				hook,
				this.mainmod$itemChance,
				this.luck,
				AnimalCrossingFishingTiming.lureLevel(this.lureSpeed)
		);
        this.mainmod$catchResult = fish.getFirst();
        this.mainmod$catchPersonality = fish.getSecond();

        this.fishAngle = Mth.nextFloat(hook.getRandom(), 0.0F, 360.0F);
        this.mainmod$orbitDegrees = this.fishAngle;
        this.mainmod$orbitDrift = (hook.getRandom().nextBoolean() ? 1.0D : -1.0D) * Mth.nextFloat(hook.getRandom(), 0.10F, 0.34F);
        this.mainmod$fishDistance = Mth.nextFloat(hook.getRandom(), 3.9F, 6.0F);
        this.mainmod$targetDistance = mainmod$bobberContactDistance();
		this.mainmod$selectedBounces = AnimalCrossingFishingTiming.rollBounceCount(
				hook.getRandom(),
				mainmod$personality()
		);
        this.mainmod$completedBounces = 0;
        this.mainmod$animationTicks = 0;
        this.mainmod$pauseTicks = mainmod$awayTicks();
        this.mainmod$shadowAge = 0;
		this.mainmod$arrivalTicks = AnimalCrossingFishShadowDisplay.ARRIVAL_TICKS;
        this.mainmod$movementTicksRemaining = 0;
        this.mainmod$phase = AnimalCrossingFishingPhase.ARRIVING;

		Display.ItemDisplay display = AnimalCrossingFishShadowDisplay.create(
				level,
				mainmod$personality()
		);
        this.mainmod$fishShadow = display;
        mainmod$positionFish(hook);
        level.addFreshEntity(display);
		AnimalCrossingFishingBobberEffects.playFishArrival(level, display);
    }

    @Unique
    private void mainmod$tickFish(ServerLevel level, FishingHook hook) {
        if (this.mainmod$fishShadow == null || !this.mainmod$fishShadow.isAlive()) {
            mainmod$spawnFish(level, hook);
            return;
        }

        this.mainmod$animationTicks++;
        mainmod$tickBobberBopRecovery(hook);
        if (mainmod$shouldScurry(level, hook)) {
            mainmod$startScurry(level, hook, 55, 120);
        }

        this.mainmod$orbitDegrees += this.mainmod$orbitDrift;
        if ((this.mainmod$animationTicks & 15) == 0 && hook.getRandom().nextFloat() < 0.35F) {
            this.mainmod$orbitDrift += (hook.getRandom().nextBoolean() ? 1.0D : -1.0D) * 0.035D;
            this.mainmod$orbitDrift = Mth.clamp((float) this.mainmod$orbitDrift, -0.42F, 0.42F);
        }

        if (this.mainmod$phase == AnimalCrossingFishingPhase.ARRIVING) {
            this.mainmod$arrivalTicks--;
            mainmod$positionFish(hook);
            if (this.mainmod$arrivalTicks <= 0) {
                mainmod$beginInitialApproach();
            }
            return;
        }

        if (this.mainmod$phase == AnimalCrossingFishingPhase.SCURRYING) {
            this.mainmod$pauseTicks--;
            this.mainmod$fishDistance += 0.06D;
            mainmod$positionFish(hook);
            if (this.mainmod$pauseTicks <= 0) {
                mainmod$cleanupFishShadow();
                this.mainmod$phase = AnimalCrossingFishingPhase.WAITING;
            }
            return;
        }

        if (this.mainmod$pauseTicks > 0) {
            this.mainmod$pauseTicks--;
            if (this.mainmod$pauseTicks <= 0 && this.mainmod$phase == AnimalCrossingFishingPhase.APPROACHING) {
                mainmod$beginApproach();
            }
        } else if (this.mainmod$phase == AnimalCrossingFishingPhase.APPROACHING) {
            if (mainmod$tickDistanceMovement()) {
                mainmod$onFishReachedBobber(level, hook);
            }
        } else if (this.mainmod$phase == AnimalCrossingFishingPhase.RETREATING) {
            if (mainmod$tickDistanceMovement()) {
                mainmod$beginAwayWaitOrApproach();
            }
        }

        mainmod$positionFish(hook);
    }

    @Unique
    private void mainmod$onFishReachedBobber(ServerLevel level, FishingHook hook) {
        if (this.mainmod$completedBounces >= this.mainmod$selectedBounces - 1) {
            mainmod$bite(level, hook);
            return;
        }

        this.mainmod$completedBounces++;
        mainmod$beginRetreat();
        mainmod$bopBobber(hook);
		AnimalCrossingFishingBobberEffects.playBounce(level, hook);
    }

    @Unique
    private void mainmod$beginApproach() {
        this.mainmod$phase = AnimalCrossingFishingPhase.APPROACHING;
        this.mainmod$targetDistance = mainmod$bobberContactDistance();
        this.mainmod$movementTicksRemaining = mainmod$personality().approachTicks();
    }

    @Unique
    private void mainmod$beginAwayWaitOrApproach() {
        this.mainmod$pauseTicks = mainmod$awayTicks();
        if (this.mainmod$pauseTicks <= 0) {
            mainmod$beginApproach();
            return;
        }

        this.mainmod$phase = AnimalCrossingFishingPhase.APPROACHING;
        this.mainmod$movementTicksRemaining = 0;
    }

    @Unique
    private void mainmod$beginInitialApproach() {
        this.mainmod$phase = AnimalCrossingFishingPhase.APPROACHING;
        this.mainmod$targetDistance = mainmod$bobberContactDistance();
        double distance = Math.max(0.0D, this.mainmod$fishDistance - this.mainmod$targetDistance);
        this.mainmod$movementTicksRemaining = mainmod$personality().initialApproachTicks(distance);
    }

    @Unique
    private void mainmod$beginRetreat() {
        this.mainmod$phase = AnimalCrossingFishingPhase.RETREATING;
        this.mainmod$targetDistance = mainmod$bobberContactDistance() + mainmod$personality().retreatDistance();
        this.mainmod$pauseTicks = 0;
        this.mainmod$movementTicksRemaining = mainmod$personality().retreatTicks();
    }

    @Unique
	private double mainmod$bobberContactDistance() {
		return AnimalCrossingFishingTiming.bobberContactDistance(mainmod$personality());
    }

	@Unique
	private boolean mainmod$tickDistanceMovement() {
		AnimalCrossingFishingTiming.DistanceMovement movement =
				AnimalCrossingFishingTiming.moveToward(
						this.mainmod$fishDistance,
						this.mainmod$targetDistance,
						this.mainmod$movementTicksRemaining
				);
		this.mainmod$fishDistance = movement.distance();
		this.mainmod$movementTicksRemaining = movement.remainingTicks();
		return movement.reachedTarget();
	}

    @Unique
	private void mainmod$bopBobber(FishingHook hook) {
		this.mainmod$bobberBopRecoveryTicks = AnimalCrossingFishingBobberEffects.bop(hook);
    }

    @Unique
	private void mainmod$tickBobberBopRecovery(FishingHook hook) {
		this.mainmod$bobberBopRecoveryTicks = AnimalCrossingFishingBobberEffects.recoverFromBop(
				hook,
				this.mainmod$bobberBopRecoveryTicks
		);
    }

    @Unique
    private void mainmod$bite(ServerLevel level, FishingHook hook) {
        this.nibble = mainmod$biteWindowTicks(hook);
        this.timeUntilLured = 0;
        this.timeUntilHooked = 0;
        this.mainmod$phase = AnimalCrossingFishingPhase.BITING;
        hook.getEntityData().set(DATA_BITING, true);
		AnimalCrossingFishingBobberEffects.playBite(level, hook);
    }

    @Unique
	private int mainmod$biteWindowTicks(FishingHook hook) {
		return AnimalCrossingFishingTiming.biteWindowTicks(hook, mainmod$personality());
    }

    @Unique
    private void mainmod$tickBite(ServerLevel level, FishingHook hook) {
        this.nibble--;
        if (this.nibble <= 0) {
            this.timeUntilLured = 0;
            this.timeUntilHooked = 0;
            hook.getEntityData().set(DATA_BITING, false);
            mainmod$startScurry(level, hook, 50, 120);
            return;
        }

		if ((this.nibble & 3) == 0) {
			AnimalCrossingFishingBobberEffects.playBiteTick(level, hook);
        }

        if (this.mainmod$fishShadow != null && this.mainmod$fishShadow.isAlive()) {
            this.mainmod$fishDistance = Math.max(mainmod$bobberContactDistance(), this.mainmod$fishDistance - 0.05D);
            this.mainmod$orbitDegrees += this.mainmod$orbitDrift * 2.0D;
            mainmod$positionFish(hook);
        }
    }

    @Unique
    private void mainmod$startCatchAnimation(FishingHook hook, ItemStack rod) {
        this.mainmod$phase = AnimalCrossingFishingPhase.CATCH_ANIMATING;
        this.mainmod$catchAnimationTicks = mainmod$personality().struggleTicks();
        this.mainmod$catchingRod = rod.copy();
        this.nibble = 0;
        this.timeUntilLured = 0;
        this.timeUntilHooked = 0;
        hook.getEntityData().set(DATA_BITING, false);
        this.mainmod$fishDistance = Math.max(this.mainmod$fishDistance, mainmod$bobberContactDistance());
        if (this.mainmod$catchAnimationTicks <= 0 && hook.level() instanceof ServerLevel level) {
            mainmod$finishCatch(level, hook);
        }
    }

    @Unique
    private void mainmod$tickCatchAnimation(ServerLevel level, FishingHook hook) {
        this.mainmod$catchAnimationTicks--;
        this.mainmod$orbitDegrees += 31.0D;
        this.mainmod$fishDistance = mainmod$bobberContactDistance() + Math.sin(this.mainmod$catchAnimationTicks * 0.45D) * 0.18D;
		AnimalCrossingFishingBobberEffects.playCatchStruggle(
				level,
				hook,
				this.mainmod$catchAnimationTicks
		);

        mainmod$positionFish(hook);

        if (this.mainmod$catchAnimationTicks <= 0) {
            mainmod$finishCatch(level, hook);
        }
    }

    @Unique
	private void mainmod$finishCatch(ServerLevel level, FishingHook hook) {
		if (hook.getPlayerOwner() instanceof ServerPlayer player) {
			AnimalCrossingFishingCatchDelivery.deliver(
					level,
					hook,
					player,
					this.mainmod$catchingRod,
					mainmod$catchResult()
			);
        }

        mainmod$cleanupFishShadow();
        hook.discard();
    }

    @Unique
	private void mainmod$positionFish(FishingHook hook) {
		if (this.mainmod$fishShadow == null) {
			return;
		}

		AnimalCrossingFishShadowDisplay.position(
				hook,
				this.mainmod$fishShadow,
				mainmod$personality(),
				new AnimalCrossingFishShadowDisplay.AnimationState(
						this.mainmod$phase,
						this.mainmod$orbitDegrees,
						this.mainmod$fishDistance,
						this.mainmod$arrivalTicks,
						this.mainmod$pauseTicks,
						this.mainmod$animationTicks,
						this.mainmod$catchAnimationTicks
				)
		);
	}

    @Unique
    private void mainmod$startScurry(ServerLevel level, FishingHook hook, int minWait, int maxWait) {
        this.mainmod$phase = AnimalCrossingFishingPhase.SCURRYING;
		this.mainmod$pauseTicks = AnimalCrossingFishShadowDisplay.SCURRY_TICKS;
        this.timeUntilLured = Mth.nextInt(hook.getRandom(), minWait, maxWait);
		AnimalCrossingFishingBobberEffects.playScurry(level, hook);
    }

    @Unique
    private boolean mainmod$tickSafetyRemoval(ServerLevel level, FishingHook hook) {
        if (this.mainmod$fishShadow == null) {
            return false;
        }

        this.mainmod$shadowAge++;
        if (this.mainmod$shadowAge < MAX_FISH_SHADOW_AGE) {
            return false;
        }

        mainmod$cleanupFishShadow();
        this.mainmod$phase = AnimalCrossingFishingPhase.WAITING;
        this.timeUntilLured = Mth.nextInt(hook.getRandom(), 40, 100);
        return true;
    }

    @Unique
    private boolean mainmod$shouldScurry(ServerLevel level, FishingHook hook) {
        if (this.mainmod$phase == AnimalCrossingFishingPhase.BITING
                || this.mainmod$phase == AnimalCrossingFishingPhase.CATCH_ANIMATING
                || this.mainmod$phase == AnimalCrossingFishingPhase.SCURRYING
                || this.mainmod$fishShadow == null) {
            return false;
        }

		return AnimalCrossingFishingEnvironment.hasNearbyThreat(
				level,
				hook,
				this.mainmod$fishShadow
		);
    }

    @Unique
    private int mainmod$awayTicks() {
        return mainmod$personality().awayTicks();
    }

    @Unique
    private FishingPersonality mainmod$personality() {
        if (this.mainmod$catchPersonality == null) {
            throw new IllegalStateException("Fishing personality requested before selecting a fish");
        }
        return mainmod$catchPersonality;
    }

    @Unique
    private ItemStack mainmod$catchResult() {
        if (this.mainmod$catchResult == null) {
            throw new IllegalStateException("Fishing result requested before selecting a fish");
        }
        return this.mainmod$catchResult;
    }

    @Unique
    private void mainmod$cleanupFishShadow() {
        if (this.mainmod$fishShadow != null) {
            this.mainmod$fishShadow.discard();
            this.mainmod$fishShadow = null;
        }
        this.mainmod$catchResult = null;
        this.mainmod$catchPersonality = null;
        this.mainmod$bobberBopRecoveryTicks = 0;
    }

}
