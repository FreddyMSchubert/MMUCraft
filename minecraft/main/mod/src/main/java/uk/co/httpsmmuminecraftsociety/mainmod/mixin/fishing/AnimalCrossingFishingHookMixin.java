package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingCatches;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingJumpScares;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

import java.util.List;

@Mixin(FishingHook.class)
public abstract class AnimalCrossingFishingHookMixin {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_BITING;
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow private float fishAngle;
    @Shadow @Final private int luck;
    @Shadow @Final private int lureSpeed;

    // Caps ping compensation so very high latency does not turn rare catches into long guaranteed windows.
    @Unique private static final int MAX_LATENCY_COMPENSATION_TICKS = 20;
    @Unique private static final int MAX_FISH_SHADOW_AGE = 20 * 65;
    @Unique private static final int ARRIVAL_TICKS = 18;
    @Unique private static final int SCURRY_TICKS = 16;
    @Unique private static final int BOBBER_BOP_RECOVERY_TICKS = 7;
    @Unique private static final double BASE_FISH_DISPLAY_WIDTH_BLOCKS = 1.22D;
    @Unique private static final double BOBBER_TOUCH_PADDING_BLOCKS = 0.10D;
    @Unique private static final int WAIT_CENTER_TICKS_WITHOUT_LURE = 20 * 30;
    @Unique private static final int WAIT_CENTER_TICKS_WITH_LURE_3 = 20 * 5;
    @Unique private static final int WAIT_SPREAD_TICKS_WITHOUT_LURE = 20 * 5;
    @Unique private static final int WAIT_SPREAD_TICKS_WITH_LURE_3 = 20 * 3;
    @Unique private static final double BOUNCE_GAUSSIAN_SIGMA = 1.0D;
    @Unique private static final double SCURRY_TRIGGER_DISTANCE = 1.15D;

    @Unique private FishingPhase mainmod$phase = FishingPhase.WAITING;
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

        if (!mainmod$isStillInWater(level, hook)) {
            mainmod$cleanupFishShadow();
            mainmod$phase = FishingPhase.WAITING;
            return;
        }

        int weatherSpeed = mainmod$getWeatherWaitSpeed(level, bobberBlockPos);
        if (mainmod$phase == FishingPhase.CATCH_ANIMATING) {
            mainmod$tickCatchAnimation(level, hook);
        } else if (this.nibble > 0) {
            mainmod$tickBite(level, hook);
        } else if (mainmod$phase == FishingPhase.WAITING) {
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

        if (mainmod$phase == FishingPhase.CATCH_ANIMATING) {
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
            this.timeUntilLured = mainmod$initialWaitTicks(hook);
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
        Pair<ItemStack, FishingPersonality> fish = FishingCatches.random(hook, this.mainmod$itemChance, this.luck);
        this.mainmod$catchResult = fish.getFirst();
        this.mainmod$catchPersonality = fish.getSecond();

        this.fishAngle = Mth.nextFloat(hook.getRandom(), 0.0F, 360.0F);
        this.mainmod$orbitDegrees = this.fishAngle;
        this.mainmod$orbitDrift = (hook.getRandom().nextBoolean() ? 1.0D : -1.0D) * Mth.nextFloat(hook.getRandom(), 0.10F, 0.34F);
        this.mainmod$fishDistance = Mth.nextFloat(hook.getRandom(), 3.9F, 6.0F);
        this.mainmod$targetDistance = mainmod$bobberContactDistance();
        this.mainmod$selectedBounces = mainmod$rollBounceCount(hook);
        this.mainmod$completedBounces = 0;
        this.mainmod$animationTicks = 0;
        this.mainmod$pauseTicks = mainmod$awayTicks();
        this.mainmod$shadowAge = 0;
        this.mainmod$arrivalTicks = ARRIVAL_TICKS;
        this.mainmod$movementTicksRemaining = 0;
        this.mainmod$phase = FishingPhase.ARRIVING;

        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setInvisible(false);

        ItemStack shadowStack = new ItemStack(Items.PAPER);
        shadowStack.set(DataComponents.ITEM_MODEL, Identifier.parse(mainmod$personality().fishShape()));
        ((ItemDisplayEntityAccessor) display).mainmod$setItemStack(shadowStack);
        ((ItemDisplayEntityAccessor) display).mainmod$setItemTransform(ItemDisplayContext.FIXED);

        DisplayEntityAccessor accessor = (DisplayEntityAccessor) display;
        accessor.mainmod$setBillboardConstraints(Display.BillboardConstraints.FIXED);
        accessor.mainmod$setTransformationInterpolationDuration(0);
        accessor.mainmod$setTransformationInterpolationDelay(0);
        accessor.mainmod$setViewRange(32.0F);
        accessor.mainmod$setShadowRadius(0.0F);
        accessor.mainmod$setShadowStrength(0.0F);
        accessor.mainmod$setWidth(1.6F);
        accessor.mainmod$setHeight(1.6F);

        this.mainmod$fishShadow = display;
        mainmod$positionFish(level, hook);
        level.addFreshEntity(display);
        level.playSound(null, display.getX(), display.getY(), display.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.20F, 0.82F);
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

        if (this.mainmod$phase == FishingPhase.ARRIVING) {
            this.mainmod$arrivalTicks--;
            mainmod$positionFish(level, hook);
            if (this.mainmod$arrivalTicks <= 0) {
                mainmod$beginInitialApproach();
            }
            return;
        }

        if (this.mainmod$phase == FishingPhase.SCURRYING) {
            this.mainmod$pauseTicks--;
            this.mainmod$fishDistance += 0.06D;
            mainmod$positionFish(level, hook);
            if (this.mainmod$pauseTicks <= 0) {
                mainmod$cleanupFishShadow();
                this.mainmod$phase = FishingPhase.WAITING;
            }
            return;
        }

        if (this.mainmod$pauseTicks > 0) {
            this.mainmod$pauseTicks--;
            if (this.mainmod$pauseTicks <= 0 && this.mainmod$phase == FishingPhase.APPROACHING) {
                mainmod$beginApproach();
            }
        } else if (this.mainmod$phase == FishingPhase.APPROACHING) {
            if (mainmod$tickDistanceMovement()) {
                mainmod$onFishReachedBobber(level, hook);
            }
        } else if (this.mainmod$phase == FishingPhase.RETREATING) {
            if (mainmod$tickDistanceMovement()) {
                mainmod$beginAwayWaitOrApproach();
            }
        }

        mainmod$positionFish(level, hook);
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
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.34F, Mth.nextFloat(hook.getRandom(), 1.15F, 1.45F));
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.06D, hook.getZ(), 5, 0.13D, 0.02D, 0.13D, 0.025D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.06D, hook.getZ(), 3, 0.11D, 0.01D, 0.11D, 0.018D);
    }

    @Unique
    private void mainmod$beginApproach() {
        this.mainmod$phase = FishingPhase.APPROACHING;
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

        this.mainmod$phase = FishingPhase.APPROACHING;
        this.mainmod$movementTicksRemaining = 0;
    }

    @Unique
    private void mainmod$beginInitialApproach() {
        this.mainmod$phase = FishingPhase.APPROACHING;
        this.mainmod$targetDistance = mainmod$bobberContactDistance();
        double distance = Math.max(0.0D, this.mainmod$fishDistance - this.mainmod$targetDistance);
        this.mainmod$movementTicksRemaining = mainmod$personality().initialApproachTicks(distance);
    }

    @Unique
    private void mainmod$beginRetreat() {
        this.mainmod$phase = FishingPhase.RETREATING;
        this.mainmod$targetDistance = mainmod$bobberContactDistance() + mainmod$personality().retreatDistance();
        this.mainmod$pauseTicks = 0;
        this.mainmod$movementTicksRemaining = mainmod$personality().retreatTicks();
    }

    @Unique
    private double mainmod$bobberContactDistance() {
        return BASE_FISH_DISPLAY_WIDTH_BLOCKS * mainmod$personality().size() * 0.5D + BOBBER_TOUCH_PADDING_BLOCKS;
    }

    @Unique
    private boolean mainmod$tickDistanceMovement() {
        if (this.mainmod$movementTicksRemaining <= 0) {
            this.mainmod$fishDistance = this.mainmod$targetDistance;
            return true;
        }

        this.mainmod$fishDistance += (this.mainmod$targetDistance - this.mainmod$fishDistance) / this.mainmod$movementTicksRemaining;
        this.mainmod$movementTicksRemaining--;
        if (this.mainmod$movementTicksRemaining <= 0) {
            this.mainmod$fishDistance = this.mainmod$targetDistance;
            return true;
        }
        return false;
    }

    @Unique
    private void mainmod$bopBobber(FishingHook hook) {
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.045D, 0.0D));
        this.mainmod$bobberBopRecoveryTicks = BOBBER_BOP_RECOVERY_TICKS;
    }

    @Unique
    private void mainmod$tickBobberBopRecovery(FishingHook hook) {
        if (this.mainmod$bobberBopRecoveryTicks <= 0) {
            return;
        }

        this.mainmod$bobberBopRecoveryTicks--;
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, 0.010D, 0.0D));
    }

    @Unique
    private void mainmod$bite(ServerLevel level, FishingHook hook) {
        this.nibble = mainmod$biteWindowTicks(hook);
        this.timeUntilLured = 0;
        this.timeUntilHooked = 0;
        this.mainmod$phase = FishingPhase.BITING;
        hook.getEntityData().set(DATA_BITING, true);
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.36D, 0.0D));
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.75F, Mth.nextFloat(hook.getRandom(), 1.05F, 1.18F));
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.55F, 0.65F);
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.12D, hook.getZ(), 18, 0.32D, 0.05D, 0.32D, 0.08D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.10D, hook.getZ(), 8, 0.24D, 0.02D, 0.24D, 0.03D);
    }

    @Unique
    private int mainmod$biteWindowTicks(FishingHook hook) {
        FishingPersonality personality = mainmod$personality();
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            int latencyCompensationTicks = Mth.ceil(player.connection.latency() / 50.0F);
            return personality.baseCatchWindowTicks() + Math.min(MAX_LATENCY_COMPENSATION_TICKS, latencyCompensationTicks);
        }
        return personality.baseCatchWindowTicks();
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
            hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.018D, 0.0D));
            level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.04D, hook.getZ(), 2, 0.08D, 0.02D, 0.08D, 0.0D);
        }

        if (this.mainmod$fishShadow != null && this.mainmod$fishShadow.isAlive()) {
            this.mainmod$fishDistance = Math.max(mainmod$bobberContactDistance(), this.mainmod$fishDistance - 0.05D);
            this.mainmod$orbitDegrees += this.mainmod$orbitDrift * 2.0D;
            mainmod$positionFish(level, hook);
        }
    }

    @Unique
    private void mainmod$startCatchAnimation(FishingHook hook, ItemStack rod) {
        this.mainmod$phase = FishingPhase.CATCH_ANIMATING;
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
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.024D, 0.0D));

        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + 0.05D, hook.getZ(), 4, 0.24D, 0.03D, 0.24D, 0.025D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.08D, hook.getZ(), 3, 0.20D, 0.02D, 0.20D, 0.02D);

        if ((this.mainmod$catchAnimationTicks & 1) == 0) {
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.34F, 1.45F);
            level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.08D, hook.getZ(), 14, 0.34D, 0.05D, 0.34D, 0.055D);
            level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + 0.08D, hook.getZ(), 8, 0.26D, 0.02D, 0.26D, 0.035D);
        }

        mainmod$positionFish(level, hook);

        if (this.mainmod$catchAnimationTicks <= 0) {
            mainmod$finishCatch(level, hook);
        }
    }

    @Unique
    private void mainmod$finishCatch(ServerLevel level, FishingHook hook) {
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            if (FishingJumpScares.shouldTrigger(hook.getRandom())) {
                FishingJumpScares.spawn(level, hook, player);
            } else {
                ItemStack result = mainmod$catchResult();
                CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, this.mainmod$catchingRod, hook, List.of(result));
                FishingCatches.catchMessage(result).ifPresent(player::sendOverlayMessage);
                FishingCatches.trackCatch(player, result);

                ItemEntity itemEntity = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), result.copy());
                double dx = player.getX() - hook.getX();
                double dy = player.getY() - hook.getY();
                double dz = player.getZ() - hook.getZ();
                itemEntity.setDeltaMovement(dx * 0.1D, dy * 0.1D + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08D, dz * 0.1D);
                level.addFreshEntity(itemEntity);
                level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ() + 0.5D, hook.getRandom().nextInt(6) + 1));
                player.awardStat(Stats.FISH_CAUGHT, 1);
            }
        }

        mainmod$cleanupFishShadow();
        hook.discard();
    }

    @Unique
    private void mainmod$positionFish(ServerLevel level, FishingHook hook) {
        if (this.mainmod$fishShadow == null) {
            return;
        }

        double radians = Math.toRadians(this.mainmod$orbitDegrees);
        double x = hook.getX() + Math.sin(radians) * this.mainmod$fishDistance;
        double z = hook.getZ() + Math.cos(radians) * this.mainmod$fishDistance;
        double y = hook.getY() + 0.035D;
        if (this.mainmod$phase == FishingPhase.ARRIVING) {
            y -= (this.mainmod$arrivalTicks / (double) ARRIVAL_TICKS) * 0.95D;
        } else if (this.mainmod$phase == FishingPhase.SCURRYING) {
            y -= (SCURRY_TICKS - this.mainmod$pauseTicks) * 0.045D;
        }

        Display.ItemDisplay display = this.mainmod$fishShadow;
        display.setPos(x, y, z);
        display.setYRot(0.0F);
        display.setXRot(0.0F);
        ((DisplayEntityAccessor) display).mainmod$setTransformation(mainmod$fishTransformation(hook, display));
    }

    @Unique
    private Transformation mainmod$fishTransformation(FishingHook hook, Display.ItemDisplay display) {
        float pulse = this.mainmod$phase == FishingPhase.APPROACHING && this.mainmod$pauseTicks <= 0
                ? 1.0F
                : 1.0F + (float) Math.sin(this.mainmod$animationTicks * 0.24D) * 0.035F;
        if (this.mainmod$phase == FishingPhase.ARRIVING) {
            pulse *= Math.max(0.08F, (ARRIVAL_TICKS - this.mainmod$arrivalTicks) / (float) ARRIVAL_TICKS);
        } else if (this.mainmod$phase == FishingPhase.SCURRYING) {
            pulse *= Math.max(0.08F, this.mainmod$pauseTicks / (float) SCURRY_TICKS);
        } else if (this.mainmod$phase == FishingPhase.CATCH_ANIMATING) {
            pulse *= 1.0F + (float) Math.sin(this.mainmod$catchAnimationTicks * 0.8D) * 0.09F;
        }

        double dx = hook.getX() - display.getX();
        double dz = hook.getZ() - display.getZ();
        float yaw = (float) Math.atan2(dz, dx);
        Quaternionf rotation = new Quaternionf()
                .rotateY(-yaw)
                .rotateX((float) Math.toRadians(90.0D));
        float size = mainmod$personality().size();
        return new Transformation(
                new Vector3f(-0.5F, -0.5F, 0.0F),
                rotation,
                new Vector3f(1.22F * pulse * size, 0.72F * pulse * size, size),
                new Quaternionf()
        );
    }

    @Unique
    private void mainmod$startScurry(ServerLevel level, FishingHook hook, int minWait, int maxWait) {
        this.mainmod$phase = FishingPhase.SCURRYING;
        this.mainmod$pauseTicks = SCURRY_TICKS;
        this.timeUntilLured = Mth.nextInt(hook.getRandom(), minWait, maxWait);
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.65F, 1.55F);
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
        this.mainmod$phase = FishingPhase.WAITING;
        this.timeUntilLured = Mth.nextInt(hook.getRandom(), 40, 100);
        return true;
    }

    @Unique
    private boolean mainmod$shouldScurry(ServerLevel level, FishingHook hook) {
        if (this.mainmod$phase == FishingPhase.BITING
                || this.mainmod$phase == FishingPhase.CATCH_ANIMATING
                || this.mainmod$phase == FishingPhase.SCURRYING
                || this.mainmod$fishShadow == null) {
            return false;
        }

        AABB box = this.mainmod$fishShadow.getBoundingBox().inflate(SCURRY_TRIGGER_DISTANCE, 0.85D, SCURRY_TRIGGER_DISTANCE);
        return !level.getEntities(
                hook,
                box,
                entity -> entity.isAlive()
                        && entity != hook.getOwner()
                        && entity != this.mainmod$fishShadow
                        && entity.distanceToSqr(this.mainmod$fishShadow) <= SCURRY_TRIGGER_DISTANCE * SCURRY_TRIGGER_DISTANCE
        ).isEmpty();
    }

    @Unique
    private boolean mainmod$isStillInWater(ServerLevel level, FishingHook hook) {
        return level.getFluidState(hook.blockPosition()).is(FluidTags.WATER);
    }

    @Unique
    private int mainmod$getWeatherWaitSpeed(ServerLevel level, BlockPos bobberBlockPos) {
        int speed = 1;
        BlockPos above = bobberBlockPos.above();
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.getRandom().nextFloat() < 0.25F && level.isRainingAt(above)) {
            speed++;
        }
        if (hook.getRandom().nextFloat() < 0.5F && !level.canSeeSky(above)) {
            speed--;
        }
        return Math.max(1, speed);
    }

    @Unique
    private int mainmod$initialWaitTicks(FishingHook hook) {
        int centerTicks = mainmod$lureScaledTicks(WAIT_CENTER_TICKS_WITHOUT_LURE, WAIT_CENTER_TICKS_WITH_LURE_3);
        int spreadTicks = mainmod$lureScaledTicks(WAIT_SPREAD_TICKS_WITHOUT_LURE, WAIT_SPREAD_TICKS_WITH_LURE_3);
        return Mth.nextInt(hook.getRandom(), Math.max(20, centerTicks - spreadTicks), centerTicks + spreadTicks);
    }

    @Unique
    private int mainmod$lureScaledTicks(int noLureTicks, int lureThreeTicks) {
        double lureProgress = mainmod$lureLevel() / 3.0D;
        return Mth.ceil(noLureTicks + (lureThreeTicks - noLureTicks) * lureProgress);
    }

    @Unique
    private int mainmod$lureLevel() {
        if (this.lureSpeed <= 3) {
            return Mth.clamp(this.lureSpeed, 0, 3);
        }
        return Mth.clamp(Math.round(this.lureSpeed / 100.0F), 0, 3);
    }

    @Unique
    private int mainmod$rollBounceCount(FishingHook hook) {
        double averageBounces = mainmod$personality().averageBounces();
        int rightEdge = Math.max(1, Mth.ceil(averageBounces * 2.0D - 1.0D));
        if (rightEdge <= 1) {
            return 1;
        }

        double totalWeight = 0.0D;
        for (int bounces = 1; bounces <= rightEdge; bounces++) {
            totalWeight += mainmod$bounceWeight(bounces, averageBounces);
        }

        double roll = hook.getRandom().nextDouble() * totalWeight;
        for (int bounces = 1; bounces <= rightEdge; bounces++) {
            roll -= mainmod$bounceWeight(bounces, averageBounces);
            if (roll <= 0.0D) {
                return bounces;
            }
        }
        return rightEdge;
    }

    @Unique
    private static double mainmod$bounceWeight(int bounces, double averageBounces) {
        double offset = (bounces - averageBounces) / BOUNCE_GAUSSIAN_SIGMA;
        return Math.exp(-0.5D * offset * offset);
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

    @Unique
    private enum FishingPhase {
        WAITING,
        ARRIVING,
        APPROACHING,
        RETREATING,
        BITING,
        SCURRYING,
        CATCH_ANIMATING
    }
}
