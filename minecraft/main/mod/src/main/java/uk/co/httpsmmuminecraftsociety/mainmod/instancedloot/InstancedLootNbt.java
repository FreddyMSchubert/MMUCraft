package uk.co.httpsmmuminecraftsociety.mainmod.instancedloot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

public final class InstancedLootNbt {
    private static final String ROOT = "PlayerInstancedLoot";
    private static final String INSTANCED = "Instanced";
    private static final String LOOT_TABLE = "LootTable";
    private static final String LOOT_TABLE_SEED = "LootTableSeed";
    private static final String PLAYERS = "Players";
    private static final String UUID_TAG = "UUID";
    private static final String SIZE = "Size";

    private InstancedLootNbt() {
    }

    public static void load(ValueInput input, InstancedLootData data, int fallbackSize) {
        data.pil$setInstancedLoot(false);
        data.pil$setInstancedLootTable(null);
        data.pil$setInstancedLootSeed(0L);
        data.pil$getPlayerLoot().clear();

        Optional<ValueInput> rootInput = input.child(ROOT);
        if (rootInput.isEmpty()) {
            return;
        }

        ValueInput root = rootInput.get();
        data.pil$setInstancedLoot(root.getBooleanOr(INSTANCED, false));
        data.pil$setInstancedLootTable(root.read(LOOT_TABLE, LootTable.KEY_CODEC).orElse(null));
        data.pil$setInstancedLootSeed(root.getLongOr(LOOT_TABLE_SEED, 0L));

        for (ValueInput playerInput : root.childrenListOrEmpty(PLAYERS)) {
            Optional<String> uuidString = playerInput.getString(UUID_TAG);
            if (uuidString.isEmpty()) {
                continue;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString.get());
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            int size = Math.max(1, playerInput.getIntOr(SIZE, fallbackSize));
            NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(playerInput, items);
            data.pil$getPlayerLoot().put(uuid, items);
        }
    }

    public static void save(ValueOutput output, InstancedLootData data) {
        if (!data.pil$isInstancedLoot()) {
            return;
        }

        ValueOutput root = output.child(ROOT);
        root.putBoolean(INSTANCED, true);
        root.storeNullable(LOOT_TABLE, LootTable.KEY_CODEC, data.pil$getInstancedLootTable());

        long seed = data.pil$getInstancedLootSeed();
        if (seed != 0L) {
            root.putLong(LOOT_TABLE_SEED, seed);
        }

        ValueOutput.ValueOutputList players = root.childrenList(PLAYERS);
        for (Map.Entry<UUID, NonNullList<ItemStack>> entry : data.pil$getPlayerLoot().entrySet()) {
            ValueOutput playerOutput = players.addChild();
            playerOutput.putString(UUID_TAG, entry.getKey().toString());
            playerOutput.putInt(SIZE, entry.getValue().size());
            ContainerHelper.saveAllItems(playerOutput, entry.getValue(), false);
        }
    }
}
