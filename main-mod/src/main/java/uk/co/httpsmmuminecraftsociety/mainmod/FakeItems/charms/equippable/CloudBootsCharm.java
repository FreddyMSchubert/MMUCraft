package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;

public final class CloudBootsCharm implements Charm, EquippedTickCallbackCharm
{
    public static final String CLOUD_BOOTS_CHARM_ID = "cosmetic-charm-cloud-boots";

    /**
     * Number of EXTRA jumps after the normal ground jump.
     *
     * 1 = double jump
     * 2 = triple jump
     * 3 = quadruple jump
     */
    public static final int DEFAULT_EXTRA_JUMPS = 1;

    /**
     * Roughly vanilla jump strength.
     */
    public static final double EXTRA_JUMP_VELOCITY = 0.42D;

    /**
     * How long a second jump press stays buffered.
     * This is what makes the jump reliable instead of frame-perfect nonsense.
     */
    public static final int JUMP_BUFFER_TICKS = 5;

    /**
     * Prevents the initial takeoff jump from instantly being mistaken for a midair jump.
     */
    public static final int MIN_AIR_TICKS_BEFORE_EXTRA_JUMP = 3;

    private static final String TAG_USED_JUMPS = "cb_used_jumps";
    private static final String TAG_RELEASED_SINCE_JUMP = "cb_released_since_jump";
    private static final String TAG_JUMP_BUFFER = "cb_jump_buffer";
    private static final String TAG_AIR_TICKS = "cb_air_ticks";

    private final int extraJumps;

    public CloudBootsCharm()
    {
        this(DEFAULT_EXTRA_JUMPS);
    }

    public CloudBootsCharm(int extraJumps)
    {
        this.extraJumps = Math.max(0, extraJumps);
    }

    @Override
    public String id()
    {
        return CLOUD_BOOTS_CHARM_ID;
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

            if (jumpBuffer > 0 && usedJumps < extraJumps && airTicks >= MIN_AIR_TICKS_BEFORE_EXTRA_JUMP) {
                Vec3 v = player.getDeltaMovement();

                double newX = v.x;
                double newY = Math.max(v.y, EXTRA_JUMP_VELOCITY);
                double newZ = v.z;

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
