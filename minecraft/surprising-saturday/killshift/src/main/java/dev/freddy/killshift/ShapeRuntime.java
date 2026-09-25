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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ShapeRuntime {
    private static final Identifier STARED_SPEED = id("stared_speed");
    private static final Identifier SQUID_FLEE_SPEED = id("squid_flee_speed");
    private static final double CEILING_INSET = 0.08;
    private static final double CEILING_PROBE = 0.5;

    private ShapeRuntime() {
    }

    static void tick(ServerPlayer player, ShapeState state) {
        MobTraits traits = state.form.traits();
        EntityType<?> type = state.form.type();

        player.setInvisible(true);
        if (state.abilityCooldown > 0) {
            state.abilityCooldown--;
        }
        if (state.angryTicks > 0) {
            state.angryTicks--;
        }
        if (state.squidFleeTicks > 0) state.squidFleeTicks--;

        if (type != EntityTypes.PLAYER) {
            player.setSprinting(false);
            player.setSwimming(false);
        }

        tickFlight(player, state);
        MobAbilities.tick(player, state);
        tickBreathing(player, state);
        tickClimbing(player, traits);
        tickBouncing(player, traits);
        tickEnvironment(player, state);
        tickLookReactions(player, state);
        tickInfiniteArrows(player, type);
        tickSquidFlee(player, state);
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

    }

    static void clear(ServerPlayer player) {
        remove(player, Attributes.MOVEMENT_SPEED, STARED_SPEED);
        remove(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED);
    }

    private static void tickSquidFlee(ServerPlayer player, ShapeState state) {
        boolean squid = state.form.type() == EntityTypes.SQUID
                || state.form.type() == EntityTypes.GLOW_SQUID;
        if (squid && state.squidFleeTicks > 0 && player.isInWater()) {
            add(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED, 0.2,
                    AttributeModifier.Operation.ADD_VALUE);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, SQUID_FLEE_SPEED);
        }
    }

    private static void tickFlight(ServerPlayer player, ShapeState state) {
        MobTraits traits = state.form.traits();
        EntityType<?> type = state.form.type();
        boolean mayFly = traits.flying() || player.isCreative() || player.isSpectator();
        boolean oldMayFly = player.getAbilities().mayfly;
        boolean oldFlying = player.getAbilities().flying;
        float oldSpeed = player.getAbilities().getFlyingSpeed();
        player.getAbilities().mayfly = mayFly;
        if (traits.flying()) {
            player.getAbilities().flying = true;
        }

        double nativeSpeed = state.form.attributes().getOrDefault(Attributes.FLYING_SPEED, 0.4);
        float speed = traits.flying() ? (float) Math.clamp(nativeSpeed * 0.125, 0.01, 0.15) : 0.05F;
        if (type == EntityTypes.GHAST || type == EntityTypes.HAPPY_GHAST) speed = 0.035F;
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

    private static void tickBreathing(ServerPlayer player, ShapeState state) {
        if (state.form.type() == EntityTypes.IRON_GOLEM) {
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }
        if (!state.form.traits().waterBreathing()) {
            return;
        }
        if (player.isEyeInFluid(FluidTags.WATER)) {
            state.landAir = player.getMaxAirSupply();
            player.setAirSupply(state.landAir);
            return;
        }

        state.landAir--;
        player.setAirSupply(state.landAir);
        if (state.landAir <= -20) {
            state.landAir = 0;
            player.hurtServer(player.level(), player.damageSources().drown(), 2.0F);
        }
    }

    private static void tickClimbing(ServerPlayer player, MobTraits traits) {
        if (!traits.wallClimber()) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        if (player.horizontalCollision) {
            double climb = Math.min(0.22, Math.max(movement.y, 0.0) + 0.08);
            player.setDeltaMovement(movement.x * 0.96, climb, movement.z * 0.96);
            player.resetFallDistance();
            syncMotion(player);
        }

        AABB ceilingProbe = player.getBoundingBox()
                .deflate(CEILING_INSET, 0.0, CEILING_INSET)
                .setMinY(player.getBoundingBox().maxY)
                .setMaxY(player.getBoundingBox().maxY + CEILING_PROBE);
        if (player.getLastClientInput().jump() && !player.level().noBlockCollision(player, ceilingProbe)) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 2, 1, false, false, false));
            player.resetFallDistance();
        }
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

        if (traits.sunSensitive()
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.level().isBrightOutside()
                && player.level().canSeeSky(BlockPos.containing(player.getEyePosition()))) {
            player.igniteForSeconds(8.0F);
        }

        if (state.form.type() == EntityTypes.ENDERMAN
                && player.tickCount % 20 == 0
                && (player.isInWaterOrRain() || player.level().isRainingAt(player.blockPosition()))) {
            player.hurtServer(player.level(), player.damageSources().drown(), 1.0F);
        }
    }

    private static void tickLookReactions(ServerPlayer player, ShapeState state) {
        boolean watched = isWatched(player);
        if (state.form.type() == EntityTypes.CREAKING && watched) {
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(0.0, movement.y, 0.0);
            syncMotion(player);
        }

        if (state.form.type() == EntityTypes.ENDERMAN && watched) {
            add(player, Attributes.MOVEMENT_SPEED, STARED_SPEED, 1.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            remove(player, Attributes.MOVEMENT_SPEED, STARED_SPEED);
        }
    }

    private static boolean isWatched(ServerPlayer target) {
        for (ServerPlayer observer : target.level().players()) {
            if (observer == target || observer.isSpectator() || observer.distanceToSqr(target) > 1024.0) {
                continue;
            }
            Vec3 toTarget = target.getEyePosition().subtract(observer.getEyePosition()).normalize();
            double alignment = observer.getLookAngle().normalize().dot(toTarget);
            double threshold = 1.0 - 0.025 / Math.max(1.0, observer.distanceTo(target));
            if (alignment > threshold && observer.hasLineOfSight(target)) {
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

    private static void tickInfiniteArrows(ServerPlayer player, EntityType<?> type) {
        boolean skeleton = type == EntityTypes.SKELETON
                || type == EntityTypes.STRAY
                || type == EntityTypes.BOGGED
                || type == EntityTypes.PARCHED;
        if (skeleton
                && player.getMainHandItem().is(Items.BOW)
                && !player.getInventory().contains(new ItemStack(Items.ARROW))) {
            player.getInventory().add(new ItemStack(Items.ARROW));
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

    private static void syncMotion(ServerPlayer player) {
        if (!player.hasDisconnected()) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
}
