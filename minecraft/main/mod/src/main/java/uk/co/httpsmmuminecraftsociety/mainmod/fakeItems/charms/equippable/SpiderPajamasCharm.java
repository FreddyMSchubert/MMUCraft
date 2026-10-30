package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

import java.util.function.Predicate;

public class SpiderPajamasCharm implements Charm, EquippedTickCallbackCharm
{
    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        if (!player.horizontalCollision || player.isInWater() || player.isInLava() || player.isShiftKeyDown()) return;

        var input = player.getLastClientInput();
        int forward = Boolean.compare(input.forward(), input.backward());
        int left = Boolean.compare(input.left(), input.right());
        if (forward == 0 && left == 0) return;

        double yaw = Math.toRadians(player.getYRot());
        Vec3 direction = new Vec3(left * Math.cos(yaw) - forward * Math.sin(yaw), 0.0D,
                forward * Math.cos(yaw) + left * Math.sin(yaw)).normalize();
        if (!isClimbableWall(player.getBoundingBox(), direction, player.maxUpStep(),
                probe -> !level.noBlockCollision(player, probe))) return;

        Vec3 v = player.getDeltaMovement();

        double maxClimbY = 0.22D;

        double newX = v.x * 0.96D;
        double newY = Math.min(maxClimbY, Math.max(v.y, 0.0D) + getClimbAccelByLevel(charmLevel));
        double newZ = v.z * 0.96D;

        player.setDeltaMovement(newX, newY, newZ);
        player.resetFallDistance();

        // sync with client
        player.setDeltaMovement(newX, newY, newZ);
        player.syncVelocity = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        player.fallDistance = 0.0F;
    }

    static boolean isClimbableWall(AABB box, Vec3 direction, float stepHeight, Predicate<AABB> blocked) {
        double x = Math.abs(direction.x);
        double z = Math.abs(direction.z);
        if (x > z * 1.2D) {
            return isClimbableWallAlong(box, Math.signum(direction.x) * 0.1D, 0.0D, stepHeight, blocked);
        }
        if (z > x * 1.2D) {
            return isClimbableWallAlong(box, 0.0D, Math.signum(direction.z) * 0.1D, stepHeight, blocked);
        }
        return x > 0.0D && z > 0.0D
                && isClimbableWallAlong(box, Math.signum(direction.x) * 0.1D, 0.0D, stepHeight, blocked)
                && isClimbableWallAlong(box, 0.0D, Math.signum(direction.z) * 0.1D, stepHeight, blocked);
    }

    private static boolean isClimbableWallAlong(AABB box, double x, double z, float stepHeight,
                                                Predicate<AABB> blocked) {
        AABB ahead = box.setMaxY(Math.min(box.maxY, box.minY + 0.6D))
                .expandTowards(x, 0.0D, z);
        if (!blocked.test(ahead)) return false;

        double top = box.minY + Math.max(3.0D, stepHeight);
        for (double y = box.minY + 1.0D; y < top; y += 1.0D) {
            if (!blocked.test(ahead.setMaxY(y + 0.05D).setMinY(y))) return false;
        }
        return blocked.test(ahead.setMaxY(top + 0.05D).setMinY(top));
    }

    private static float getClimbAccelByLevel(int level) {
        return (level * 0.025f) + 0.025f;
    }
}
