package uk.co.httpsmmuminecraftsociety.mainmod.size;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SizeDiscsCheck {
    public static void main(String[] args) {
        assert SizeDiscs.nextScale(1, 0.6, 2, true) == 2;
        assert SizeDiscs.nextScale(2, 0.6, 2, true) == 4;
        assert SizeDiscs.nextScale(4, 0.6, 2, true) == 6;
        assert SizeDiscs.nextScale(6, 0.6, 2, false) == 4;
        assert SizeDiscs.nextScale(4, 0.6, 2, false) == 2;
        assert SizeDiscs.nextScale(2, 0.6, 2, false) == 1;
        assert SizeDiscs.nextScale(12 / 1.95, 0.6, 1.95, false) == 4;
        double villagerFloor = 0.2 / 0.6;
        assert SizeDiscs.nextScale(0.5, 0.6, 1.95, false) == villagerFloor;
        assert SizeDiscs.nextScale(villagerFloor, 0.6, 1.95, false) == villagerFloor;
        assert SizeDiscs.nextScale(villagerFloor, 0.6, 1.95, true) == 0.5;
        assert SizeDiscs.nextScale(1, 0.2, 0.4, false) == 0.5;
        BlockPos wall = new BlockPos(3, 4, 5);
        assert SizeDiscs.targetBlock(new BlockHitResult(Vec3.ZERO, Direction.WEST, wall, false))
                .equals(wall.west());
    }
}
