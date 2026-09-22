package uk.co.httpsmmuminecraftsociety.mainmod.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;

/** Finds the island underside, then uses vanilla ore generation for the vein. */
public record BottomEndStoneFeature(OreFeature ore) implements Feature {
    public static final MapCodec<BottomEndStoneFeature> CODEC = OreFeature.CODEC.xmap(
            BottomEndStoneFeature::new, BottomEndStoneFeature::ore);

    @Override
    public MapCodec<BottomEndStoneFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
            pos.set(origin.getX(), y, origin.getZ());
            var state = level.getBlockState(pos);
            if (!state.isAir()) {
                return state.is(Blocks.END_STONE) && ore.place(level, generator, random, pos);
            }
        }
        return false;
    }
}
