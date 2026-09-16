package uk.co.httpsmmuminecraftsociety.mainmod.inventoryview;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;

import java.nio.file.Files;
import java.nio.file.Path;

final class PlayerDataPersistence {
    private PlayerDataPersistence() {}

    static void save(MinecraftServer server, ServerPlayer player) {
        Path playerDataDirectory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        try (ProblemReporter.ScopedCollector problems =
                     new ProblemReporter.ScopedCollector(player.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(problems, player.registryAccess());
            player.saveWithoutId(output);
            CompoundTag data = output.buildResult();
            Path temporary = Files.createTempFile(playerDataDirectory, player.getStringUUID() + "-", ".dat");
            NbtIo.writeCompressed(data, temporary);
            Path current = playerDataDirectory.resolve(player.getStringUUID() + ".dat");
            Path previous = playerDataDirectory.resolve(player.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(current, temporary, previous);
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Failed to save offline inventory edits for {}", player.getName().getString(), exception);
        }
    }
}
