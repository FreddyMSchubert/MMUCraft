package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;

import java.util.Map;
import java.util.WeakHashMap;

public final class WingedShoesCharm implements Charm, EquippedTickCallbackCharm, TickCallbackCharm
{
    public static final double EXTRA_JUMP_VELOCITY = LivingEntity.BASE_JUMP_POWER;
    private static final int SLOW_FALLING_TICKS = 20;
    // Kept on the player until landing so swapping gear or reconnecting cannot clear the jump's penalty.
    private static final String AIRBORNE_TAG = "mainmod.winged_shoes_jump";

    // How long a second jump press stays buffered.
    public static final int JUMP_BUFFER_TICKS = 5;

    public static final int MIN_AIR_TICKS_BEFORE_EXTRA_JUMP = 3;

    private final Map<ServerPlayer, JumpState> jumpStates = new WeakHashMap<>();

    private static final class JumpState {
        int usedJumps;
        boolean releasedSinceJump;
        int jumpBuffer;
        int airTicks;
        boolean slowFallingApplied;
    }

    private static int getExtraJumpsForLevel(int level) {
        return level >= 10 ? level + 6 : level;
    }

    public static boolean hasActiveJump(ServerPlayer player) {
        return player.entityTags().contains(AIRBORNE_TAG);
    }

    @Override
    public void onTick(ServerPlayer player, ServerLevel level) {
        JumpState state = jumpStates.get(player);
        if (player.onGround()) {
            player.removeTag(AIRBORNE_TAG);
            if (state != null) {
                if (state.slowFallingApplied) {
                    MobEffectInstance effect = player.getEffect(MobEffects.SLOW_FALLING);
                    if (effect != null && effect.getAmplifier() == 0 && effect.getDuration() <= SLOW_FALLING_TICKS) {
                        player.removeEffect(MobEffects.SLOW_FALLING);
                    }
                }
                state.usedJumps = 0;
                state.releasedSinceJump = false;
                state.jumpBuffer = 0;
                state.airTicks = 0;
                state.slowFallingApplied = false;
            }
            return;
        }

        if (!hasActiveJump(player)) return;
        // Match vanilla Slow Falling even if milk cleared the effect earlier this tick.
        player.resetFallDistance();
        MobEffectInstance effect = player.getEffect(MobEffects.SLOW_FALLING);
        if (effect == null || (effect.getAmplifier() == 0 && effect.getDuration() <= 1)) {
            boolean applied = player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, false, false));
            if (state != null) state.slowFallingApplied = applied;
        }
    }

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        JumpState state = jumpStates.get(player);
        if (state == null) {
            state = new JumpState();
            // A reconnect in midair must not grant another set of extra jumps.
            if (hasActiveJump(player)) state.usedJumps = getExtraJumpsForLevel(charmLevel);
            jumpStates.put(player, state);
        }
        if (player.onGround()) return;

        state.airTicks++;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            state.releasedSinceJump = false;
            state.jumpBuffer = 0;
            return;
        }

        boolean jumpHeld = player.getLastClientInput().jump();
        if (!jumpHeld) state.releasedSinceJump = true;
        if (jumpHeld && state.releasedSinceJump) state.jumpBuffer = JUMP_BUFFER_TICKS;
        else if (state.jumpBuffer > 0) state.jumpBuffer--;

        if (state.jumpBuffer > 0 && state.usedJumps < getExtraJumpsForLevel(charmLevel) && state.airTicks >= MIN_AIR_TICKS_BEFORE_EXTRA_JUMP) {
            if (!hasActiveJump(player) && !player.addTag(AIRBORNE_TAG)) return;
            Vec3 v = player.getDeltaMovement();
            double newY = Math.max(v.y, EXTRA_JUMP_VELOCITY);
            player.setDeltaMovement(v.x, newY, v.z);
            player.setOnGround(false);
            player.resetFallDistance();
            player.syncVelocity = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            for (int i = 0; i < 10; i++) {
                double x = (level.getRandom().nextDouble() - 0.5) * 0.7;
                double z = (level.getRandom().nextDouble() - 0.5) * 0.7;
                level.sendParticles(ParticleTypes.POOF, player.getX() + x, player.getY() + 0.1,
                        player.getZ() + z, 0, x * 0.25, -0.12, z * 0.25, 1);
            }

            state.usedJumps++;
            state.releasedSinceJump = false;
            state.jumpBuffer = 0;
            state.airTicks = 0;
            state.slowFallingApplied = player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, false, false));
        }
    }
}
