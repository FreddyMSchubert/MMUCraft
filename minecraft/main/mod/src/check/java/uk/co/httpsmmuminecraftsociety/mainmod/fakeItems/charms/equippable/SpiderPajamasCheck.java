package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class SpiderPajamasCheck {
    private static final AABB PLAYER = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D);
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);

    public static void main(String[] args) {
        assert !canClimb(wall(2.0D), 0.6F);
        assert !canClimb(wall(3.0D), 0.6F);
        assert canClimb(wall(4.0D), 0.6F);
        assert canClimb(wall(4.0D), 3.5F);
        assert !canClimb(wall(4.0D), 4.0F);
        assert !canClimb(wall(4.0D), 4.5F);
        assert !canClimb(probe -> probe.intersects(new AABB(-1.0D, 1.0D, 0.3D, 1.0D, 4.0D, 1.0D)), 0.6F);
        assert !canClimb(probe -> probe.intersects(new AABB(-1.0D, 0.0D, 0.3D, 1.0D, 1.0D, 1.0D))
                || probe.intersects(new AABB(-1.0D, 3.0D, 0.3D, 1.0D, 4.0D, 1.0D)), 0.6F);
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, FORWARD.scale(-1.0D), 0.6F, wall(4.0D));

        Vec3 diagonal = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, diagonal, 0.6F, wall(4.0D));
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, new Vec3(2.0D, 0.0D, 1.0D).normalize(), 0.6F, wall(4.0D));
        assert SpiderPajamasCharm.isClimbableWall(PLAYER, new Vec3(0.2D, 0.0D, 1.0D).normalize(), 0.6F, wall(4.0D));
        Predicate<AABB> eastWall = probe -> probe.intersects(new AABB(0.3D, 0.0D, -1.0D, 1.0D, 4.0D, 1.0D));
        assert SpiderPajamasCharm.isClimbableWall(PLAYER, diagonal, 0.6F,
                probe -> wall(4.0D).test(probe) || eastWall.test(probe));
    }

    private static boolean canClimb(Predicate<AABB> blocked, float stepHeight) {
        return SpiderPajamasCharm.isClimbableWall(PLAYER, FORWARD, stepHeight, blocked);
    }

    private static Predicate<AABB> wall(double height) {
        AABB wall = new AABB(-1.0D, 0.0D, 0.3D, 1.0D, height, 1.0D);
        return wall::intersects;
    }
}
