package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class SpiderPajamasCheck {
    private static final AABB PLAYER = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D);
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);

    public static void main(String[] args) {
        assert !canClimb(wall(0.5D), 0.6F);
        assert canClimb(wall(2.0D), 0.6F);
        assert canClimb(wall(3.0D), 0.6F);
        assert !canClimb(wall(2.0D), 2.1F);
        assert canClimb(wall(3.0D), 2.1F);
        assert canClimb(wall(4.0D), 0.6F);
        assert canClimb(wall(4.0D), 3.5F);
        assert !canClimb(wall(4.0D), 4.0F);
        assert !canClimb(wall(4.0D), 4.5F);
        assert !canClimb(probe -> probe.intersects(new AABB(-1.0D, 1.0D, 0.3D, 1.0D, 4.0D, 1.0D)), 0.6F);
        AABB overhang = new AABB(-1.0D, 2.0D, -1.0D, 1.0D, 2.5D, 0.3D);
        assert !canClimb(probe -> wall(3.0D).test(probe) || probe.intersects(overhang), 0.6F);
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, FORWARD.scale(-1.0D), 0.6F, false, wall(4.0D));

        AABB nearTop = PLAYER.move(0.0D, 3.8D, 0.0D);
        assert !SpiderPajamasCharm.isClimbableWall(nearTop, FORWARD, 0.6F, false, wall(4.0D));
        assert SpiderPajamasCharm.isClimbableWall(nearTop, FORWARD, 0.6F, true, wall(4.0D));
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER.move(0.0D, 4.1D, 0.0D), FORWARD, 0.6F, true, wall(4.0D));

        Vec3 diagonal = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, diagonal, 0.6F, false, wall(4.0D));
        assert SpiderPajamasCharm.isClimbableWall(PLAYER, facing(25.0D), 0.6F, false, wall(4.0D));
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, facing(35.0D), 0.6F, false, wall(4.0D));
        Predicate<AABB> eastWall = probe -> probe.intersects(new AABB(0.3D, 0.0D, -1.0D, 1.0D, 4.0D, 1.0D));
        assert SpiderPajamasCharm.isClimbableWall(PLAYER, new Vec3(1.0D, 0.0D, 0.0D), 0.6F, false, eastWall);
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, FORWARD, 0.6F, false, eastWall);
        assert !SpiderPajamasCharm.isClimbableWall(PLAYER, diagonal, 0.6F, false,
                probe -> wall(4.0D).test(probe) || eastWall.test(probe));
    }

    private static boolean canClimb(Predicate<AABB> blocked, float stepHeight) {
        return SpiderPajamasCharm.isClimbableWall(PLAYER, FORWARD, stepHeight, false, blocked);
    }

    private static Predicate<AABB> wall(double height) {
        AABB wall = new AABB(-1.0D, 0.0D, 0.3D, 1.0D, height, 1.0D);
        return wall::intersects;
    }

    private static Vec3 facing(double angleFromSouth) {
        double radians = Math.toRadians(angleFromSouth);
        return new Vec3(Math.sin(radians), 0.0D, Math.cos(radians));
    }
}
