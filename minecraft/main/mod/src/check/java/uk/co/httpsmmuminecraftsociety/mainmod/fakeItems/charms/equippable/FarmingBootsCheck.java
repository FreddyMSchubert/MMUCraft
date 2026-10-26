package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;

public final class FarmingBootsCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var wart = Blocks.NETHER_WART.defaultBlockState();
        assert FarmingBootsCharm.replantedState(wart) == null;
        assert FarmingBootsCharm.replantedState(wart.setValue(NetherWartBlock.AGE, 2)) == null;
        assert FarmingBootsCharm.replantedState(wart.setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE)) == wart;

        var wheat = Blocks.WHEAT.defaultBlockState();
        assert FarmingBootsCharm.replantedState(wheat) == null;
        assert FarmingBootsCharm.replantedState(wheat.setValue(CropBlock.AGE, CropBlock.MAX_AGE)) == wheat;
        assert FarmingBootsCharm.replantedState(Blocks.STONE.defaultBlockState()) == null;
    }
}
