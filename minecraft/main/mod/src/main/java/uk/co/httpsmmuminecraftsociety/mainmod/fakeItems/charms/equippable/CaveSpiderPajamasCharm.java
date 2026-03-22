package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class CaveSpiderPajamasCharm implements Charm, EquippedTickCallbackCharm
{
    private static final double CEILING_PROBE = 0.06D;

    private static final double WALK_SPEED = 0.28D;
    private static final double SPRINT_SPEED = 0.42D;

    private static final double CONTROL = 0.35D;
    private static final double BRAKE = 0.55D;

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        Input input = player.getLastClientInput();
        if (!input.jump()) return stack;

        boolean hasCeiling =
                !level.noBlockCollision(player, player.getBoundingBox().move(0.0D, CEILING_PROBE, 0.0D));
        if (!hasCeiling) return stack;

        player.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION,
                1,
                1,
                false,
                false,
                false
        ));

        Vec3 v = player.getDeltaMovement();

        float yawRad = player.getYRot() * ((float)Math.PI / 180F);
        Vec3 forward = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);

        Vec3 wish = Vec3.ZERO;
        if (input.forward())  wish = wish.add(forward);
        if (input.backward()) wish = wish.subtract(forward);
        if (input.left())     wish = wish.add(left);
        if (input.right())    wish = wish.subtract(left);

        boolean sprinting = input.sprint();

        double newX = v.x;
        double newZ = v.z;

        if (wish.lengthSqr() > 1.0E-6D) {
            wish = wish.normalize();

            double targetSpeed = sprinting ? SPRINT_SPEED : WALK_SPEED;
            double targetX = wish.x * targetSpeed;
            double targetZ = wish.z * targetSpeed;

            newX += (targetX - newX) * CONTROL;
            newZ += (targetZ - newZ) * CONTROL;
        } else {
            newX *= (1.0D - BRAKE);
            newZ *= (1.0D - BRAKE);

            if (Math.abs(newX) < 0.003D) newX = 0.0D;
            if (Math.abs(newZ) < 0.003D) newZ = 0.0D;
        }

        double stickY = 0.08D;
        double maxStickY = 0.10D;
        double newY = Math.min(maxStickY, Math.max(v.y, 0.0D) + stickY);

        player.setDeltaMovement(newX, newY, newZ);
        player.resetFallDistance();

        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        return stack;
    }
}
