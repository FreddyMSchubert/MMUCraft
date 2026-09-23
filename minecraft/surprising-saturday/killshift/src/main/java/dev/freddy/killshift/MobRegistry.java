package dev.freddy.killshift;

import java.util.Set;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class MobRegistry {
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

    private static final Set<EntityType<?>> UNDEAD = Set.of(
            EntityTypes.BOGGED, EntityTypes.DROWNED, EntityTypes.HUSK,
            EntityTypes.PARCHED, EntityTypes.PHANTOM, EntityTypes.SKELETON,
            EntityTypes.SKELETON_HORSE, EntityTypes.STRAY, EntityTypes.WITHER,
            EntityTypes.WITHER_SKELETON, EntityTypes.ZOGLIN, EntityTypes.ZOMBIE,
            EntityTypes.ZOMBIE_HORSE, EntityTypes.ZOMBIE_NAUTILUS,
            EntityTypes.ZOMBIE_VILLAGER, EntityTypes.ZOMBIFIED_PIGLIN
    );

    private static final Set<EntityType<?>> WALL_CLIMBERS = Set.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER);
    private static final Set<EntityType<?>> BOUNCY = Set.of(EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.SULFUR_CUBE);
    private static final Set<EntityType<?>> LAVA_SAFE = Set.of(
            EntityTypes.BLAZE, EntityTypes.MAGMA_CUBE, EntityTypes.STRIDER,
            EntityTypes.WITHER, EntityTypes.WITHER_SKELETON
    );

    private static final Set<EntityType<?>> QUICK = Set.of(
            EntityTypes.CAT, EntityTypes.CAVE_SPIDER,
            EntityTypes.ENDERMAN, EntityTypes.FOX, EntityTypes.OCELOT,
            EntityTypes.RABBIT, EntityTypes.SPIDER, EntityTypes.VEX, EntityTypes.WOLF
    );

    private static final Set<EntityType<?>> STRONG_JUMPERS = Set.of(
            EntityTypes.CAMEL, EntityTypes.CAMEL_HUSK, EntityTypes.DONKEY,
            EntityTypes.GOAT, EntityTypes.HORSE, EntityTypes.MULE,
            EntityTypes.RABBIT, EntityTypes.SKELETON_HORSE, EntityTypes.ZOMBIE_HORSE
    );

    private MobRegistry() {
    }

    @SuppressWarnings("unchecked")
    static MobForm createForm(Mob mob) {
        EntityType<?> type = mob.getType();
        MobTraits traits = new MobTraits(
                AQUATIC.contains(type),
                WATER_BREATHING.contains(type),
                FLYING.contains(type),
                FLYING.contains(type),
                UNDEAD.contains(type),
                WALL_CLIMBERS.contains(type),
                BOUNCY.contains(type),
                LAVA_SAFE.contains(type),
                FLYING.contains(type) || type == EntityTypes.CAT,
                QUICK.contains(type) ? 1.2 : 1.0,
                STRONG_JUMPERS.contains(type) ? 1.75 : 1.0,
                type == EntityTypes.GHAST || type == EntityTypes.HAPPY_GHAST ? 0.035F : 0.05F
        );

        double attack = mob.getAttribute(Attributes.ATTACK_DAMAGE) == null
                ? 1.0
                : mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double armor = mob.getAttribute(Attributes.ARMOR) == null
                ? 0.0
                : mob.getAttributeValue(Attributes.ARMOR);
        double scale = Math.clamp(mob.getBbWidth() / 0.6, 0.35, 4.0);

        return new MobForm(
                (EntityType<? extends Mob>) type,
                traits,
                Math.max(1.0F, mob.getMaxHealth()),
                Math.max(1.0, attack),
                armor,
                scale
        );
    }

    static boolean isFriendly(EntityType<?> mob, EntityType<?> shape) {
        if (mob == shape) {
            return true;
        }
        if (UNDEAD.contains(mob) && UNDEAD.contains(shape)) {
            return true;
        }
        if (WALL_CLIMBERS.contains(mob) && WALL_CLIMBERS.contains(shape)) {
            return true;
        }
        return mob == EntityTypes.CREEPER && shape == EntityTypes.SKELETON;
    }
}
