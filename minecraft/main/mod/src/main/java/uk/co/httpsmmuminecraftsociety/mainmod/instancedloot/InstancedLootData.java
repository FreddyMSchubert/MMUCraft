package uk.co.httpsmmuminecraftsociety.mainmod.instancedloot;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

public interface InstancedLootData {
    boolean pil$isInstancedLoot();

    void pil$setInstancedLoot(boolean instanced);

    ResourceKey<LootTable> pil$getInstancedLootTable();

    void pil$setInstancedLootTable(ResourceKey<LootTable> lootTable);

    long pil$getInstancedLootSeed();

    void pil$setInstancedLootSeed(long seed);

    Map<UUID, NonNullList<ItemStack>> pil$getPlayerLoot();

    void pil$markInstancedLootDirty();
}
