package dev.freddy.chunklocked;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

final class LootRoller {
    record Offer(Identifier item, int count) { }

    Offer roll(ServerLevel level, Identifier biome, BlockPos center) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, new Vec3(center.getX(), center.getY(), center.getZ()))
                .create(LootContextParamSets.CHEST);
        ItemStack item = roll(level, params, "all_biome");
        if (biome.getNamespace().equals("minecraft")) {
            LootTable table = table(level, biome.getPath());
            if (table != LootTable.EMPTY) {
                item = singleItem(table, params, biome.getPath());
                if (biome.getPath().equals("deep_frozen_ocean")) {
                    // The 2.1.0 function rolls this biome twice and keeps the second item.
                    item = singleItem(table, params, biome.getPath());
                }
            }
        }
        return new Offer(BuiltInRegistries.ITEM.getKey(item.getItem()), item.getCount());
    }

    private ItemStack roll(ServerLevel level, LootParams params, String biome) {
        LootTable table = table(level, biome);
        if (table == LootTable.EMPTY) throw new IllegalStateException("Missing Chunklock loot table: " + biome);
        return singleItem(table, params, biome);
    }

    private LootTable table(ServerLevel level, String biome) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath("chunklock", "biome/" + biome));
        return level.getServer().reloadableRegistries().getLootTable(key);
    }

    private ItemStack singleItem(LootTable table, LootParams params, String biome) {
        var items = table.getRandomItems(params);
        if (items.size() != 1) throw new IllegalStateException("Chunklock loot table did not roll one item: " + biome);
        return items.getFirst();
    }
}
