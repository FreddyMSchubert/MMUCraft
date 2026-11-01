package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public class SpiderPajamasCharm implements Charm, EquippedTickCallbackCharm
{
    private static final double MAX_CLIMB_Y = 0.22D;
    private static final double MIN_FACING_DOT = Math.cos(Math.toRadians(30.0D));
    private static final Map<ServerPlayer, Integer> LAST_CLIMB_TICK = new WeakHashMap<>();

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        Integer lastClimbTick = LAST_CLIMB_TICK.remove(player);
        boolean continuing = lastClimbTick != null
                && (lastClimbTick == player.tickCount || lastClimbTick == player.tickCount - 1);
        if ((!continuing && !player.horizontalCollision)
                || player.isInWater() || player.isInLava() || player.isShiftKeyDown()) return;

        var input = player.getLastClientInput();
        if (!input.forward() || input.backward()) return;

        double yaw = Math.toRadians(player.getYRot());
        Vec3 facing = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        if (!isClimbableWall(player.getBoundingBox(), facing, player.maxUpStep(), continuing,
                probe -> !level.noBlockCollision(player, probe))) return;

        LAST_CLIMB_TICK.put(player, player.tickCount);

        Vec3 v = player.getDeltaMovement();

        double newX = v.x * 0.96D;
        double newY = Math.min(MAX_CLIMB_Y, Math.max(v.y, 0.0D) + getClimbAccelByLevel(charmLevel));
        double newZ = v.z * 0.96D;

        player.setDeltaMovement(newX, newY, newZ);
        player.resetFallDistance();

        // sync with client
        player.setDeltaMovement(newX, newY, newZ);
        player.syncVelocity = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        player.fallDistance = 0.0F;
    }

    static boolean isClimbableWall(AABB box, Vec3 facing, float stepHeight, boolean continuing,
                                   Predicate<AABB> blocked) {
        if (blocked.test(box.expandTowards(0.0D, MAX_CLIMB_Y, 0.0D))) return false;

        if (Math.abs(facing.x) >= MIN_FACING_DOT
                && isClimbableWallAlong(box, Math.signum(facing.x) * 0.1D, 0.0D, stepHeight, continuing, blocked)) {
            return true;
        }
        return Math.abs(facing.z) >= MIN_FACING_DOT
                && isClimbableWallAlong(box, 0.0D, Math.signum(facing.z) * 0.1D, stepHeight, continuing, blocked);
    }

    private static boolean isClimbableWallAlong(AABB box, double x, double z, float stepHeight,
                                                boolean continuing, Predicate<AABB> blocked) {
        AABB ahead = box.setMaxY(Math.min(box.maxY, box.minY + 0.6D))
                .expandTowards(x, 0.0D, z);
        if (!blocked.test(ahead)) return false;
        if (continuing) return true;

        double top = box.minY + stepHeight;
        for (double y = box.minY + 1.0D; y < top; y += 1.0D) {
            if (!blocked.test(ahead.setMaxY(y + 0.05D).setMinY(y))) return false;
        }
        return blocked.test(ahead.setMaxY(top + 0.05D).setMinY(top));
    }

    private static float getClimbAccelByLevel(int level) {
        return (level * 0.025f) + 0.025f;
    }
}
