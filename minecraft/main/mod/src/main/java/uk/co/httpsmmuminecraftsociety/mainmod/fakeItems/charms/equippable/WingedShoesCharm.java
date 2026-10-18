package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

import java.util.Map;
import java.util.WeakHashMap;

public final class WingedShoesCharm implements Charm, EquippedTickCallbackCharm
{
    public static final double EXTRA_JUMP_VELOCITY = 0.62D;
    private static final int SLOW_FALLING_TICKS = 20;

    // How long a second jump press stays buffered.
    public static final int JUMP_BUFFER_TICKS = 5;

    public static final int MIN_AIR_TICKS_BEFORE_EXTRA_JUMP = 3;

    private final Map<ServerPlayer, JumpState> jumpStates = new WeakHashMap<>();

    private static final class JumpState {
        final ItemStack stack;
        int usedJumps;
        boolean releasedSinceJump;
        int jumpBuffer;
        int airTicks;

        JumpState(ItemStack stack) {
            this.stack = stack;
        }
    }

    private static int getExtraJumpsForLevel(int level) {
        return level >= 10 ? level + 6 : level;
    }

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        JumpState state = jumpStates.get(player);
        if (state == null || state.stack != stack) {
            state = new JumpState(stack);
            jumpStates.put(player, state);
        }

        int usedJumps = state.usedJumps;
        boolean releasedSinceJump = state.releasedSinceJump;
        int jumpBuffer = state.jumpBuffer;
        int airTicks = state.airTicks;

        boolean jumpHeld = player.getLastClientInput().jump();
        boolean onGround = player.onGround();

        if (onGround) {
            usedJumps = 0;
            releasedSinceJump = false;
            jumpBuffer = 0;
            airTicks = 0;
        } else {
            airTicks++;

            // Arm the next extra jump once the player has released jump after takeoff / after the last extra jump.
            if (!jumpHeld) {
                releasedSinceJump = true;
            }

            // If the player presses jump again after releasing it, keep that press buffered for a few ticks.
            if (jumpHeld && releasedSinceJump) {
                jumpBuffer = JUMP_BUFFER_TICKS;
            } else if (jumpBuffer > 0) {
                jumpBuffer--;
            }

            if (jumpBuffer > 0 && usedJumps < getExtraJumpsForLevel(charmLevel) && airTicks >= MIN_AIR_TICKS_BEFORE_EXTRA_JUMP) {
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

                usedJumps++;
                releasedSinceJump = false; // require another release before another extra jump
                jumpBuffer = 0;
                airTicks = 0;
            }

            if (usedJumps > 0 && airTicks % (SLOW_FALLING_TICKS - 1) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, false, false));
            }
        }

        state.usedJumps = usedJumps;
        state.releasedSinceJump = releasedSinceJump;
        state.jumpBuffer = jumpBuffer;
        state.airTicks = airTicks;
    }
}
