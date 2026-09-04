package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeleportPotionUtils
{
    private static final Map<UUID, Long> LAST_DAMAGE_TICK = new HashMap<>();
    private static final long RECENT_DAMAGE_COOLDOWN_TICKS = 20 * 60; // 1 minute
    private static final int MIN_TELEPORT_LIGHT = 8;

    public static String checkTeleportable(ServerPlayer player, ServerLevel level, double horRange, double vertRange) {
        return checkTeleportable(player, level, horRange, vertRange, true);
    }

    public static String checkTeleportableAfterSelfDamage(ServerPlayer player, ServerLevel level, double horRange, double vertRange) {
        return checkTeleportable(player, level, horRange, vertRange, false);
    }

    private static String checkTeleportable(ServerPlayer player, ServerLevel level, double horRange, double vertRange, boolean checkRecentDamage) {
        if (player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) return "";

        Long last_damage_tick = LAST_DAMAGE_TICK.get(player.getUUID());
        if (checkRecentDamage && last_damage_tick != null && level.getGameTime() - last_damage_tick < RECENT_DAMAGE_COOLDOWN_TICKS)
            return "You may not teleport now; you took damage within the last minute.";

        if (player.isInLava())
            return "You may not teleport now; you are in lava.";
        if (player.isOnFire())
            return "You may not teleport now; you are on fire.";
        if (player.isInWater() || player.isUnderWater() || player.getAirSupply() < player.getMaxAirSupply())
            return "You may not teleport now; you are in water.";
        if (player.isInPowderSnow)
            return "You may not teleport now; you are in powder snow.";
        if (player.isChangingDimension())
            return "You may not teleport now; you are changing dimensions.";
        if (!player.onGround())
            return "You may not teleport now; you are not on solid ground.";
        if (level.getMaxLocalRawBrightness(player.blockPosition()) < MIN_TELEPORT_LIGHT)
            return "You may not teleport now; it is too dark.";
        if (player.isFreezing())
            return "You may not teleport now; you are freezing.";
        if (player.isFallFlying())
            return "You may not teleport now; you are gliding.";
        AABB area = player.getBoundingBox().inflate(horRange, vertRange, horRange);
        if (!level.getEntitiesOfClass(Monster.class, area, LivingEntity::isAlive).isEmpty())
            return "You may not teleport now; there are monsters nearby.";

        return "";
    }

    public static void teleportWithCompanions(
            String potion,
            ServerPlayer player,
            ServerLevel destination,
            double x,
            double y,
            double z,
            float yRot,
            float xRot
    ) {
        ServerLevel source = player.level();
        Vec3 origin = player.position();
        String sourceDimension = source.dimension().identifier().toString();
        long gameTime = source.getGameTime();
        boolean member = PlayerStatsSync.isMember(player);
        if (!member) {
            player.stopRiding();
            player.ejectPassengers();
        }
        Entity vehicle = player.isPassenger() ? player.getRootVehicle() : null;
        Set<Entity> leashed = member ? Leashable.leashableLeashedTo(player).stream()
                .map(leashable -> (Entity) leashable)
                .collect(java.util.stream.Collectors.toSet()) : Set.of();
        Map<Entity, Set<String>> companionRoles = new LinkedHashMap<>();
        leashed.forEach(entity -> addRole(companionRoles, entity, "leashed"));

        Set<Entity> vehicleTree = new LinkedHashSet<>();
        if (vehicle != null) {
            vehicle.getSelfAndPassengers().forEach(entity -> {
                vehicleTree.add(entity);
                if (entity != player) addRole(companionRoles, entity, entity == vehicle ? "mount" : "passenger");
            });
        }

        if (member) {
            // ponytail: scans loaded entities; index active pets by owner if potion use makes this hot.
            source.getAllEntities().forEach(entity -> {
                if (entity instanceof TamableAnimal pet
                        && pet.isTame()
                        && !pet.isOrderedToSit()
                        && pet.getOwner() == player) {
                    addRole(companionRoles, pet, "pet");
                }
            });
        }

        List<String> movedCompanions = new ArrayList<>();
        List<String> failedCompanions = new ArrayList<>();
        boolean playerMoved;
        if (vehicle == null) {
            playerMoved = player.teleport(new TeleportTransition(
                    destination,
                    new Vec3(x, y, z),
                    Vec3.ZERO,
                    yRot,
                    xRot,
                    Set.of(),
                    TeleportTransition.DO_NOTHING
            )) != null;
        } else {
            Vec3 vehicleDestination = new Vec3(x, y, z).subtract(player.position().subtract(vehicle.position()));
            playerMoved = teleportEntity(vehicle, destination, vehicleDestination) != null;
            vehicleTree.stream()
                    .filter(entity -> entity != player)
                    .map(entity -> describeEntity(entity, companionRoles.get(entity)))
                    .forEach((playerMoved ? movedCompanions : failedCompanions)::add);
        }

        companionRoles.forEach((entity, roles) -> {
            if (!vehicleTree.contains(entity)) {
                    Entity moved = teleportEntity(entity, destination, new Vec3(x, y, z));
                    if (moved instanceof Leashable movedLeashable && leashed.contains(entity)) {
                        movedLeashable.setLeashedTo(player, true);
                    }
                    (moved == null ? failedCompanions : movedCompanions).add(describeEntity(entity, roles));
            }
        });

        MainMod.LOGGER.info(
                "Teleport potion={} gameTime={} player=\"{}\" playerUuid={} from={} ({}, {}, {}) requestedDestination={} ({}, {}, {}) actualDestination={} ({}, {}, {}) success={} companionsMoved={} companionsFailed={}",
                potion,
                gameTime,
                safeName(player),
                player.getUUID(),
                sourceDimension,
                origin.x(),
                origin.y(),
                origin.z(),
                destination.dimension().identifier(),
                x,
                y,
                z,
                player.level().dimension().identifier(),
                player.getX(),
                player.getY(),
                player.getZ(),
                playerMoved,
                movedCompanions,
                failedCompanions
        );
    }

    private static void addRole(Map<Entity, Set<String>> roles, Entity entity, String role) {
        roles.computeIfAbsent(entity, ignored -> new LinkedHashSet<>()).add(role);
    }

    private static String describeEntity(Entity entity, Set<String> roles) {
        return "type=" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                + " name=\"" + safeName(entity) + "\" uuid=" + entity.getUUID()
                + " roles=" + roles;
    }

    private static String safeName(Entity entity) {
        return entity.getName().getString().replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private static Entity teleportEntity(Entity entity, ServerLevel destination, Vec3 position) {
        return entity.teleport(new TeleportTransition(
                destination,
                position,
                Vec3.ZERO,
                entity.getYRot(),
                entity.getXRot(),
                Set.of(),
                TeleportTransition.DO_NOTHING
        ));
    }

    public static void onLivingEntityDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked)
    {
        if (entity instanceof ServerPlayer player) {
            LAST_DAMAGE_TICK.put(player.getUUID(), player.level().getGameTime());
        }
    }
}
