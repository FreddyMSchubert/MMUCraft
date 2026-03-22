package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportPotionUtils
{
    private static final Map<UUID, Long> LAST_DAMAGE_TICK = new HashMap<>();
    private static final long RECENT_DAMAGE_COOLDOWN_TICKS = 20 * 60 * 3; // 3 minutes

    public static String checkTeleportable(ServerPlayer player, ServerLevel level, double horRange, double vertRange) {
        if (player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) return "";

        Long last_damage_tick = LAST_DAMAGE_TICK.get(player.getUUID());
        if (last_damage_tick != null && level.getGameTime() - last_damage_tick < RECENT_DAMAGE_COOLDOWN_TICKS)
            return "You may not teleport now; you took damage within the last 3 minutes.";

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
        if (level.getLightEmission(player.blockPosition()) < 10)
            return "You may not teleport now; it is too dark.";
        if (player.isFreezing())
            return "You may not teleport now; you are freezing.";
        if (player.isFallFlying())
            return "You may not teleport now; you are gliding.";
        if (player.isPassenger())
            return "You may not teleport now; you are riding something.";

        AABB area = player.getBoundingBox().inflate(horRange, vertRange, horRange);
        if (!level.getEntitiesOfClass(Monster.class, area, LivingEntity::isAlive).isEmpty())
            return "You may not teleport now; there are monsters nearby.";

        return "";
    }

    public static void onLivingEntityDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked)
    {
        if (entity instanceof ServerPlayer player) {
            LAST_DAMAGE_TICK.put(player.getUUID(), player.level().getGameTime());
        }
    }
}
