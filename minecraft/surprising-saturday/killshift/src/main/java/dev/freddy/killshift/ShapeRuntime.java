package dev.freddy.killshift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class ShapeRuntime {
    private static final Identifier STARED_SPEED = id("stared_speed");
    private static final Identifier FROZEN_SPEED = id("creaking_frozen_speed");
    private static final Identifier SQUID_FLEE_SPEED = id("squid_flee_speed");
    private static final Identifier SQUID_FLEE_WATER = id("squid_flee_water");
    private static final Identifier AXOLOTL_LAND_SPEED = id("axolotl_land_speed");
    private static final Identifier HOP_GROUND_SPEED = id("hop_ground_speed");

    private ShapeRuntime() {
    }

    static void tick(ServerPlayer player, ShapeState state) {
        MobTraits traits = state.form.traits();
        EntityType<?> type = state.form.type();

        ShapeManager.hidePlayer(player);
        if (state.abilityCooldown > 0) {
            state.abilityCooldown--;
        }
        if (state.angryTicks > 0) {
            state.angryTicks--;
        }
        if (state.squidFleeTicks > 0) state.squidFleeTicks--;

        if (type != EntityTypes.PLAYER) {
            if (!ShapeManager.canSprintAsMob(player)) player.setSprinting(false);
            if (!ShapeManager.canSwimAsMob(player)) player.setSwimming(false);
        }

        tickFlight(player, state);
        tickHopSpeed(player);
        tickAxolotlLandSpeed(player, type);
        tickLandMovement(player, traits);
        MobAbilities.tick(player, state);
        tickBreathing(player, state);
        tickClimbing(player, traits);
        tickBouncing(player, traits);
        tickEnvironment(player, state);
        tickLookReactions(player, state);
        tickSquidFlee(player, state);
        tickPiglinConversion(player, state);
        if (type == EntityTypes.IRON_GOLEM && player.isInWater()) {
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x * 0.8,
                    Math.min(-0.06, Math.max(-0.12, movement.y)), movement.z * 0.8);
            syncMotion(player);
        }
        if (type == EntityTypes.CHICKEN && !player.onGround()
                && player.getDeltaMovement().y < -0.1) {
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, -0.1, movement.z);
            syncMotion(player);
        }

        if (type == EntityTypes.VEX) {
            player.noPhysics = true;
        }
        if (type == EntityTypes.CAT) {
            repelCreepers(player);
        }
        if (type == EntityTypes.WOLF) {
            repelSkeletons(player);
        }

    }

    static void clear(ServerPlayer player) {
        remove(player, Attributes.MOVEMENT_SPEED, STARED_SPEED);
        remove(player, Attributes.MOVEMENT_SPEED, FROZEN_SPEED);
        remove(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED);
        remove(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SQUID_FLEE_WATER);
        remove(player, Attributes.MOVEMENT_SPEED, AXOLOTL_LAND_SPEED);
        remove(player, Attributes.MOVEMENT_SPEED, HOP_GROUND_SPEED);
    }

    private static void tickHopSpeed(ServerPlayer player) {
        if (ShapeManager.landHopSpeed(player) >= 0.0 && player.onGround()) {
            add(player, Attributes.MOVEMENT_SPEED, HOP_GROUND_SPEED, -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, HOP_GROUND_SPEED);
        }
    }

    private static void tickSquidFlee(ServerPlayer player, ShapeState state) {
        boolean squid = state.form.type() == EntityTypes.SQUID
                || state.form.type() == EntityTypes.GLOW_SQUID;
        if (squid && state.squidFleeTicks > 0 && player.isInWater()) {
            add(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED, 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            add(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SQUID_FLEE_WATER, 0.4,
                    AttributeModifier.Operation.ADD_VALUE);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED);
            remove(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SQUID_FLEE_WATER);
        }
    }

    private static void tickAxolotlLandSpeed(ServerPlayer player, EntityType<?> type) {
        if (type == EntityTypes.AXOLOTL && !player.isInWaterOrRain()) {
            add(player, Attributes.MOVEMENT_SPEED, AXOLOTL_LAND_SPEED, -0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, AXOLOTL_LAND_SPEED);
        }
    }

    private static void tickPiglinConversion(ServerPlayer player, ShapeState state) {
        EntityType<?> type = state.form.type();
        if (type != EntityTypes.PIGLIN && type != EntityTypes.PIGLIN_BRUTE) return;
        boolean immune = state.viewData.getBooleanOr("IsImmuneToZombification", false);
        boolean converts = player.level().environmentAttributes()
                .getValue(EnvironmentAttributes.PIGLINS_ZOMBIFY, player.position());
        state.piglinExposureTicks = !immune && converts ? state.piglinExposureTicks + 1 : 0;
        if (state.piglinExposureTicks > 300) {
            ShapeManager.convertTo(player, EntityTypes.ZOMBIFIED_PIGLIN);
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
        }
    }

    private static void tickFlight(ServerPlayer player, ShapeState state) {
        MobTraits traits = state.form.traits();
        EntityType<?> type = state.form.type();
        boolean forcedFlight = traits.flying() && (type != EntityTypes.BLAZE || !player.isInWater());
        boolean mayFly = forcedFlight || player.isCreative() || player.isSpectator();
        boolean oldMayFly = player.getAbilities().mayfly;
        boolean oldFlying = player.getAbilities().flying;
        float oldSpeed = player.getAbilities().getFlyingSpeed();
        player.getAbilities().mayfly = mayFly;
        if (traits.flying()) {
            player.getAbilities().flying = forcedFlight;
        }

        double nativeSpeed = state.form.attributes().getOrDefault(Attributes.FLYING_SPEED, 0.4);
        float speed = traits.flying() ? (float) Math.clamp(nativeSpeed * 0.0625, 0.01, 0.08) : 0.05F;
        if (type == EntityTypes.GHAST || type == EntityTypes.HAPPY_GHAST) speed = 0.025F;
        if (state.form.type() == EntityTypes.BEE && state.angryTicks > 0) {
            speed *= 1.8F;
            if (state.view instanceof Bee bee) {
                bee.setPersistentAngerEndTime(bee.level().getGameTime() + state.angryTicks);
            }
        }

        player.getAbilities().setFlyingSpeed(speed);
        if (oldMayFly != player.getAbilities().mayfly
                || oldFlying != player.getAbilities().flying
                || oldSpeed != speed) {
            player.onUpdateAbilities();
        }
    }

    private static void tickLandMovement(ServerPlayer player, MobTraits traits) {
        if (!traits.landImmobile() || traits.flying() || player.isInWater()) return;
        Vec3 movement = player.getDeltaMovement();
        double max = player.onGround() ? 0.0 : 0.025;
        double horizontal = Math.hypot(movement.x, movement.z);
        if (horizontal <= max) return;
        double factor = max / horizontal;
        player.setDeltaMovement(movement.x * factor, movement.y, movement.z * factor);
        syncMotion(player);
    }

    private static void tickBreathing(ServerPlayer player, ShapeState state) {
        EntityType<?> type = state.form.type();
        if (type == EntityTypes.IRON_GOLEM || type == EntityTypes.COPPER_GOLEM
                || type == EntityTypes.FROG || type == EntityTypes.WITHER_SKELETON
                || type == EntityTypes.DROWNED
                || type == EntityTypes.HAPPY_GHAST || type == EntityTypes.TURTLE
                || type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN) {
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }
        if (!state.form.traits().waterBreathing()) {
            return;
        }
        int maxLandAir = state.view == null ? player.getMaxAirSupply() : state.view.getMaxAirSupply();
        if (player.isEyeInFluid(FluidTags.WATER)
                || type == EntityTypes.AXOLOTL && player.isInWaterOrRain()) {
            state.landAir = maxLandAir;
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }

        state.landAir--;
        player.setAirSupply((int) Math.ceil(Math.max(0, state.landAir)
                * (double) player.getMaxAirSupply() / Math.max(1, maxLandAir)));
        if (state.landAir <= -20) {
            state.landAir = 0;
            player.hurtServer(player.level(), player.damageSources().drown(), 2.0F);
        }
    }

    private static void tickClimbing(ServerPlayer player, MobTraits traits) {
        if (!traits.wallClimber()) {
            return;
        }

        var input = player.getLastClientInput();
        boolean moving = input.forward() || input.backward() || input.left() || input.right() || input.jump();
        double inset = Math.min(0.2, player.getBbHeight() * 0.2);
        AABB wallProbe = player.getBoundingBox().deflate(0.0, inset, 0.0).inflate(0.08, 0.0, 0.08);
        if (!moving || (!player.horizontalCollision && player.level().noBlockCollision(player, wallProbe))) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        double climb = Math.min(0.22, Math.max(movement.y, 0.0) + 0.08);
        player.setDeltaMovement(movement.x * 0.96, climb, movement.z * 0.96);
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 2, 1, false, false, false));
        player.resetFallDistance();
        syncMotion(player);
    }

    private static void tickBouncing(ServerPlayer player, MobTraits traits) {
        if (!traits.bouncy() || !player.onGround()) {
            return;
        }
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(0.0, movement.y, 0.0);
        syncMotion(player);
    }

    private static void tickEnvironment(ServerPlayer player, ShapeState state) {
        MobTraits traits = state.form.traits();
        if (traits.lavaSafe()) {
            player.clearFire();
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 10, 0, false, false, false));
        }

        if (ShapeManager.isWaterSensitive(player) && player.isInWaterOrRain()) {
            player.hurtServer(player.level(), player.damageSources().drown(), 1.0F);
        }
        if (traits.sunSensitive() && player.isInWaterOrRain()) player.clearFire();

        if (traits.sunSensitive()
                && !player.isInWaterOrRain()
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.level().isBrightOutside()
                && player.level().canSeeSky(BlockPos.containing(player.getEyePosition()))) {
            player.igniteForSeconds(8.0F);
        }
    }

    private static void tickLookReactions(ServerPlayer player, ShapeState state) {
        if (state.form.type() != EntityTypes.CREAKING && state.form.type() != EntityTypes.ENDERMAN) return;
        boolean watched = isWatched(player, state.form.type() == EntityTypes.CREAKING);
        if (state.form.type() == EntityTypes.CREAKING && watched) {
            add(player, Attributes.MOVEMENT_SPEED, FROZEN_SPEED, -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            player.setSprinting(false);
            player.setShiftKeyDown(false);
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(0.0, movement.y, 0.0);
            syncMotion(player);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, FROZEN_SPEED);
        }

        if (state.form.type() == EntityTypes.ENDERMAN && watched) {
            add(player, Attributes.MOVEMENT_SPEED, STARED_SPEED, 1.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, STARED_SPEED);
        }
    }

    static boolean isWatched(ServerPlayer target, boolean includeMobs) {
        ShapeState state = ShapeManager.get(target);
        LivingEntity view = state == null ? target : state.view;
        if (view == null) view = target;
        AABB box = view.getBoundingBox();
        for (LivingEntity observer : target.level().getEntitiesOfClass(LivingEntity.class, box.inflate(32.0))) {
            if (observer == target || observer == view || !observer.isAlive() || observer.isSpectator()
                    || observer.entityTags().contains(ShapeView.VIEW_TAG)
                    || !(observer instanceof ServerPlayer) && (!includeMobs || !(observer instanceof Mob))) continue;
            Vec3 eye = observer.getEyePosition();
            Vec3 end = eye.add(Vec3.directionFromRotation(observer.getXRot(), observer.getYHeadRot()).scale(32.0));
            var hit = box.clip(eye, end);
            if (hit.isPresent() && target.level().clip(new ClipContext(eye, hit.get(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private static void repelCreepers(ServerPlayer player) {
        for (Creeper creeper : player.level().getEntitiesOfClass(
                Creeper.class,
                player.getBoundingBox().inflate(6.0),
                creeper -> creeper.isAlive()
        )) {
            Vec3 away = creeper.position().subtract(player.position());
            if (away.lengthSqr() > 0.01) {
                creeper.setDeltaMovement(creeper.getDeltaMovement().add(away.normalize().scale(0.12)));
                creeper.setTarget(null);
            }
        }
    }

    private static void repelSkeletons(ServerPlayer player) {
        for (AbstractSkeleton skeleton : player.level().getEntitiesOfClass(
                AbstractSkeleton.class, player.getBoundingBox().inflate(6.0),
                skeleton -> skeleton.isAlive()
        )) {
            Vec3 away = skeleton.position().subtract(player.position());
            if (away.lengthSqr() > 0.01) {
                skeleton.setDeltaMovement(skeleton.getDeltaMovement().add(away.normalize().scale(0.12)));
                skeleton.setTarget(null);
            }
        }
    }

    private static void add(
            ServerPlayer player,
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Killshift.MOD_ID, path);
    }

    static void syncMotion(ServerPlayer player) {
        if (!player.hasDisconnected()) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
}
