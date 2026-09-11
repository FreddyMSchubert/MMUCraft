package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SlimeDetectorCharm implements Charm, UseCallbackCharm {
    public static final int CHARM_ID = 55;
    private static final float LOADING_MODEL = 6.0F;
    private static final int MIN_SCAN_TICKS = 10;
    private static final int MAX_SCAN_TICKS = 40;

    // Vanilla combines this fixed salt with the current world's seed and the chunk coordinates.
    // It is not a replacement for the world seed; changing worlds still changes the result.
    private static final long VANILLA_SLIME_CHUNK_SALT = 987234911L;

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        if (isLoading(stack)) return InteractionResult.SUCCESS_SERVER;

        int bars = signalBars(level.getSeed(), player.chunkPosition());
        setModel(stack, LOADING_MODEL);

        int delayTicks = randomScanDelayTicks(level.getRandom());
        CompletableFuture.delayedExecutor(delayTicks * 50L, TimeUnit.MILLISECONDS).execute(() ->
                level.getServer().execute(() -> {
                    if (!isLoading(stack)) return;
                    setModel(stack, bars);
                    player.getInventory().setChanged();
                })
        );
        return InteractionResult.SUCCESS_SERVER;
    }

    static int signalBars(long seed, ChunkPos origin) {
        for (int distance = 0; distance <= 4; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dz = distance - Math.abs(dx);
                if (isSlimeChunk(seed, origin.x() + dx, origin.z() + dz)
                        || dz != 0 && isSlimeChunk(seed, origin.x() + dx, origin.z() - dz)) {
                    return 5 - distance;
                }
            }
        }
        return 0;
    }

    static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, VANILLA_SLIME_CHUNK_SALT).nextInt(10) == 0;
    }

    static int randomScanDelayTicks(RandomSource random) {
        return MIN_SCAN_TICKS + random.nextInt(MAX_SCAN_TICKS - MIN_SCAN_TICKS + 1);
    }

    private static boolean isLoading(ItemStack stack) {
        List<Float> values = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).floats();
        return !values.isEmpty() && values.getFirst() == LOADING_MODEL;
    }

    private static void setModel(ItemStack stack, float value) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(value), data.flags(), data.strings(), data.colors()));
    }
}
