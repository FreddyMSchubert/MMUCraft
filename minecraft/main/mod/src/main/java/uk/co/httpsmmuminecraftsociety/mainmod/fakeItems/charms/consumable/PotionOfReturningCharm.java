package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

import java.util.Set;

public class PotionOfReturningCharm implements Charm, ConsumableCallbacksCharm
{
    public static final int DRINK_DURATION_TICKS = 15 * 20;
    public static final int POST_DRINK_BAD_EFFECT_DURATION = 3 * 20;
    public static final int POST_DRINK_GOOD_EFFECT_DURATION = 15 * 20;
    public static final int DARKNESS_START_TICKS = DRINK_DURATION_TICKS / 100 * 50;
    public static final int LEVITATION_START_TICKS = DRINK_DURATION_TICKS / 100 * 75;

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        int effectDuration = DRINK_DURATION_TICKS - elapsedTicks;
        effectDuration += POST_DRINK_BAD_EFFECT_DURATION;

        applyEffectIfNotYetApplied(player, MobEffects.POISON, effectDuration, 0);
        applyEffectIfNotYetApplied(player, MobEffects.HUNGER, effectDuration, 0);
        applyEffectIfNotYetApplied(player, MobEffects.SLOWNESS, effectDuration, 4);
        applyEffectIfNotYetApplied(player, MobEffects.WEAKNESS, effectDuration, 4);
        applyEffectIfNotYetApplied(player, MobEffects.MINING_FATIGUE, effectDuration, 4);
        applyEffectIfNotYetApplied(player, MobEffects.UNLUCK, effectDuration, 4);
        applyEffectIfNotYetApplied(player, MobEffects.NAUSEA, effectDuration, 255);

        if (elapsedTicks == 0) {
            applyEffectIfNotYetApplied(player, MobEffects.INSTANT_DAMAGE, 1, 1);
        }
        if (elapsedTicks >= DARKNESS_START_TICKS) {
            applyEffectIfNotYetApplied(player, MobEffects.DARKNESS, effectDuration + 60, 0);
        }
        if (elapsedTicks >= LEVITATION_START_TICKS) {
            applyEffectIfNotYetApplied(player, MobEffects.LEVITATION, effectDuration - POST_DRINK_BAD_EFFECT_DURATION, 1);
        }
    }

    @Override
    public void onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            player.stopUsingItem();
            return;
        }

        BlockPos spawn = level.getRespawnData().globalPos().pos();

        MainMod.LOGGER.info("Teleporting (Portion of Returning) player " + player.getName().getString() + " from (" + player.getX() + ", " + player.getY() + ", " + player.getZ() + ") to spawn (" + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ() + ").");

        player.teleportTo(
                level,
                spawn.getX() + 0.5,
                spawn.getY() + 10.5,
                spawn.getZ() + 0.5,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                false
        );

        player.fallDistance = 0.0F;

        applyEffectIfNotYetApplied(player, MobEffects.SLOW_FALLING, POST_DRINK_GOOD_EFFECT_DURATION, 0);
        applyEffectIfNotYetApplied(player, MobEffects.REGENERATION, POST_DRINK_GOOD_EFFECT_DURATION, 0);
        applyEffectIfNotYetApplied(player, MobEffects.INSTANT_HEALTH, POST_DRINK_GOOD_EFFECT_DURATION, 3);
        applyEffectIfNotYetApplied(player, MobEffects.NIGHT_VISION, POST_DRINK_GOOD_EFFECT_DURATION, 3);
        applyEffectIfNotYetApplied(player, MobEffects.SPEED, POST_DRINK_GOOD_EFFECT_DURATION, 0);
        applyEffectIfNotYetApplied(player, MobEffects.STRENGTH, POST_DRINK_GOOD_EFFECT_DURATION, 0);
        applyEffectIfNotYetApplied(player, MobEffects.FIRE_RESISTANCE, POST_DRINK_GOOD_EFFECT_DURATION, 0);
        applyEffectIfNotYetApplied(player, MobEffects.SATURATION, POST_DRINK_GOOD_EFFECT_DURATION, 0);

        stack.consume(1, player);
    }

    private static void applyEffectIfNotYetApplied(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        if (player.hasEffect(effect) && player.getEffect(effect).getDuration() > duration && player.getEffect(effect).getAmplifier() > amplifier) return;
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }
}
