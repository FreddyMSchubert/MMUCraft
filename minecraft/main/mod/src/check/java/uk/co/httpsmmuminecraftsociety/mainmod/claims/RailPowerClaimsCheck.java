package uk.co.httpsmmuminecraftsociety.mainmod.claims;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.List;

public final class RailPowerClaimsCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 1);
        BlockState off = Blocks.POWERED_RAIL.defaultBlockState();
        BlockState on = off.setValue(PoweredRailBlock.POWERED, true);

        assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, on);
        ClaimsManager.beginRailPowerUpdate(null, first, off, on);
        try {
            assert ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, on);
            assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, second, off, on);
            assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, first, on, on);

            ClaimsManager.beginRailPowerUpdate(null, second, on, off);
            try {
                assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, on);
                assert ClaimsManager.isAuthorizedRailPowerUpdate(null, second, on, off);
            } finally {
                ClaimsManager.endRailPowerUpdate();
            }
            assert ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, on);
        } finally {
            ClaimsManager.endRailPowerUpdate();
        }
        assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, on);

        for (BlockState forbidden : List.of(
                off,
                Blocks.AIR.defaultBlockState(),
                off.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST),
                on.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST),
                on.setValue(BaseRailBlock.WATERLOGGED, true))) {
            ClaimsManager.beginRailPowerUpdate(null, first, off, forbidden);
            try {
                assert !ClaimsManager.isAuthorizedRailPowerUpdate(null, first, off, forbidden);
            } finally {
                ClaimsManager.endRailPowerUpdate();
            }
        }
    }
}
