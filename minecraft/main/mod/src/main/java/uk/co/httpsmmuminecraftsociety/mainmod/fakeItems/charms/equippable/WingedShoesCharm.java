package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public final class WingedShoesCharm implements Charm, EquippedTickCallbackCharm
{
    public static final String WINGED_SHOES_CHARM_ID_BEGINNING = "cosmetic-charm-winged-shoes-";
    private final int level;

    public static final double EXTRA_JUMP_VELOCITY = 0.62D;
    public static final double FORWARD_BOOST = 0.28D;

    // How long a second jump press stays buffered.
    public static final int JUMP_BUFFER_TICKS = 5;

    public static final int MIN_AIR_TICKS_BEFORE_EXTRA_JUMP = 3;

    private static final String TAG_USED_JUMPS = "ws_used_jumps";
    private static final String TAG_RELEASED_SINCE_JUMP = "ws_released_since_jump";
    private static final String TAG_JUMP_BUFFER = "ws_jump_buffer";
    private static final String TAG_AIR_TICKS = "ws_air_ticks";

    public WingedShoesCharm(int level)
    {
        this.level = level;
    }

    @Override
    public String id()
    {
        return WINGED_SHOES_CHARM_ID_BEGINNING + level;
    }

    private static int getExtraJumpsForLevel(int level) {
        return switch (level)
        {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 6;
            default -> throw new IllegalStateException("Unexpected winged shoes level: " + level);
        };
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();

        int usedJumps = tag.getIntOr(TAG_USED_JUMPS, 0);
        boolean releasedSinceJump = tag.getBoolean(TAG_RELEASED_SINCE_JUMP).orElse(false);
        int jumpBuffer = tag.getIntOr(TAG_JUMP_BUFFER, 0);
        int airTicks = tag.getIntOr(TAG_AIR_TICKS, 0);

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

            if (jumpBuffer > 0 && usedJumps < getExtraJumpsForLevel(this.level) && airTicks >= MIN_AIR_TICKS_BEFORE_EXTRA_JUMP) {
                Vec3 v = player.getDeltaMovement();

                // Get the direction the player is facing, flattened to horizontal.
                Vec3 look = player.getLookAngle();
                Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);

                if (horizontalLook.lengthSqr() > 1.0E-6D) {
                    horizontalLook = horizontalLook.normalize();
                } else {
                    horizontalLook = Vec3.ZERO;
                }

                double newX = v.x + horizontalLook.x * FORWARD_BOOST;
                double newY = Math.max(v.y, EXTRA_JUMP_VELOCITY);
                double newZ = v.z + horizontalLook.z * FORWARD_BOOST;

                player.setDeltaMovement(newX, newY, newZ);
                player.setOnGround(false);
                player.resetFallDistance();

                player.hurtMarked = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                usedJumps++;
                releasedSinceJump = false; // require another release before another extra jump
                jumpBuffer = 0;
                airTicks = 0;
            }
        }

        tag.putInt(TAG_USED_JUMPS, usedJumps);
        tag.putBoolean(TAG_RELEASED_SINCE_JUMP, releasedSinceJump);
        tag.putInt(TAG_JUMP_BUFFER, jumpBuffer);
        tag.putInt(TAG_AIR_TICKS, airTicks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return stack;
    }
}
