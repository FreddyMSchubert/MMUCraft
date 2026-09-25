package dev.freddy.killshift;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class MobRegistry {
    static final double PLAYER_MOVEMENT_FACTOR = 0.4;
    static final List<Holder<Attribute>> COPIED_ATTRIBUTES = List.of(
            Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS,
            Attributes.ATTACK_DAMAGE, Attributes.ATTACK_KNOCKBACK, Attributes.ATTACK_SPEED,
            Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, Attributes.FALL_DAMAGE_MULTIPLIER,
            Attributes.FLYING_SPEED, Attributes.GRAVITY, Attributes.JUMP_STRENGTH,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED,
            Attributes.SAFE_FALL_DISTANCE, Attributes.STEP_HEIGHT,
            Attributes.WATER_MOVEMENT_EFFICIENCY
    );

    private static final Set<EntityType<?>> AQUATIC = Set.of(
            EntityTypes.AXOLOTL, EntityTypes.COD, EntityTypes.DOLPHIN,
            EntityTypes.DROWNED, EntityTypes.ELDER_GUARDIAN, EntityTypes.GLOW_SQUID,
            EntityTypes.GUARDIAN, EntityTypes.NAUTILUS, EntityTypes.PUFFERFISH,
            EntityTypes.SALMON, EntityTypes.SQUID, EntityTypes.TADPOLE,
            EntityTypes.TROPICAL_FISH, EntityTypes.TURTLE, EntityTypes.ZOMBIE_NAUTILUS
    );

    private static final Set<EntityType<?>> FLYING = Set.of(
            EntityTypes.ALLAY, EntityTypes.BAT, EntityTypes.BEE, EntityTypes.BLAZE,
            EntityTypes.ENDER_DRAGON, EntityTypes.GHAST, EntityTypes.HAPPY_GHAST,
            EntityTypes.PARROT, EntityTypes.PHANTOM, EntityTypes.VEX, EntityTypes.WITHER
    );

    private static final Set<EntityType<?>> WATER_BREATHING = Set.of(
            EntityTypes.AXOLOTL, EntityTypes.COD, EntityTypes.ELDER_GUARDIAN,
            EntityTypes.GLOW_SQUID, EntityTypes.GUARDIAN, EntityTypes.NAUTILUS,
            EntityTypes.PUFFERFISH, EntityTypes.SALMON, EntityTypes.SQUID,
            EntityTypes.TADPOLE, EntityTypes.TROPICAL_FISH, EntityTypes.ZOMBIE_NAUTILUS
    );


    private static final Set<EntityType<?>> WALL_CLIMBERS = Set.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER);
    private static final Set<EntityType<?>> BOUNCY = Set.of(EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.SULFUR_CUBE);
    private static final Set<EntityType<?>> LAVA_SAFE = Set.of(
            EntityTypes.BLAZE, EntityTypes.MAGMA_CUBE, EntityTypes.STRIDER,
            EntityTypes.WITHER, EntityTypes.WITHER_SKELETON
    );

    private static final Set<EntityType<?>> LAND_IMMOBILE = Set.of(
            EntityTypes.BEE, EntityTypes.COD, EntityTypes.DOLPHIN, EntityTypes.ELDER_GUARDIAN,
            EntityTypes.GLOW_SQUID, EntityTypes.GUARDIAN, EntityTypes.NAUTILUS,
            EntityTypes.PUFFERFISH, EntityTypes.SALMON, EntityTypes.SQUID,
            EntityTypes.TADPOLE, EntityTypes.TROPICAL_FISH, EntityTypes.ZOMBIE_NAUTILUS
    );

    private MobRegistry() {
    }

    static boolean supports(EntityType<?> type) {
        return type != EntityTypes.GIANT;
    }

    static MobTraits traits(EntityType<?> type) {
        return new MobTraits(
                AQUATIC.contains(type),
                WATER_BREATHING.contains(type),
                FLYING.contains(type),
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.BURN_IN_DAYLIGHT),
                WALL_CLIMBERS.contains(type),
                BOUNCY.contains(type),
                LAVA_SAFE.contains(type),
                LAND_IMMOBILE.contains(type)
        );
    }

    static MobForm createForm(LivingEntity source) {
        EntityType<?> type = source.getType();
        MobTraits traits = traits(type);
        Map<Holder<Attribute>, Double> values = new HashMap<>();
        for (Holder<Attribute> attribute : COPIED_ATTRIBUTES) {
            AttributeInstance instance = source.getAttribute(attribute);
            if (instance != null) values.put(attribute, instance.getValue());
        }
        values.put(Attributes.MAX_HEALTH, Math.max(1.0, source.getMaxHealth()));
        values.put(Attributes.ATTACK_DAMAGE, Math.max(1.0, values.getOrDefault(Attributes.ATTACK_DAMAGE, 1.0)));
        if (type != EntityTypes.PLAYER && !traits.landImmobile()) {
            values.computeIfPresent(Attributes.MOVEMENT_SPEED,
                    (attribute, speed) -> speed * PLAYER_MOVEMENT_FACTOR);
        }
        if (traits.aquatic()) values.merge(Attributes.MOVEMENT_SPEED, 0.1, Math::max);
        if (traits.aquatic()) values.put(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
        if (traits.flying() || type == EntityTypes.CHICKEN || type == EntityTypes.CAT) {
            values.put(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
        }
        double scale = type == EntityTypes.PLAYER ? 1.0 : Math.clamp(
                Math.max(source.getEyeHeight() * 1.1, source.getBbHeight() * 1.25)
                        / EntityTypes.PLAYER.getDimensions().eyeHeight(), 0.0625, 16.0);
        return new MobForm(type, traits, values, scale);
    }

    static boolean isFriendly(EntityType<?> mob, EntityType<?> shape) {
        if (mob == shape) {
            return true;
        }
        if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob).is(EntityTypeTags.UNDEAD)
                && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(shape).is(EntityTypeTags.UNDEAD)) {
            return true;
        }
        if (WALL_CLIMBERS.contains(mob) && WALL_CLIMBERS.contains(shape)) {
            return true;
        }
        return mob == EntityTypes.CREEPER && shape == EntityTypes.SKELETON;
    }
}
