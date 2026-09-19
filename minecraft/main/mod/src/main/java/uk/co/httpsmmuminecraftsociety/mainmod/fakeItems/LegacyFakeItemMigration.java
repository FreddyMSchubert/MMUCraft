package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

/** Rebase older custom items when players carry them or open their containers. */
public final class LegacyFakeItemMigration {
    private static int ticks;

    private LegacyFakeItemMigration() {}

    public static void tick(MinecraftServer server) {
        if (++ticks % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) migratePlayer(player);
    }

    public static void migratePlayer(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack old = player.getInventory().getItem(i);
            ItemStack updated = migrate(old);
            if (updated != old) player.getInventory().setItem(i, updated);
        }
        for (Slot slot : player.containerMenu.slots) {
            ItemStack old = slot.getItem();
            ItemStack updated = migrate(old);
            if (updated != old) slot.set(updated);
        }
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack updatedCarried = migrate(carried);
        if (updatedCarried != carried) player.containerMenu.setCarried(updatedCarried);
    }

    public static void migrateEntity(Entity entity) {
        if (entity instanceof ItemEntity droppedItem) {
            ItemStack old = droppedItem.getItem();
            ItemStack updated = migrate(old);
            if (updated != old) droppedItem.setItem(updated);
        } else if (entity instanceof ItemFrame frame) {
            ItemStack old = frame.getItem();
            ItemStack updated = migrate(old);
            if (updated != old) frame.setItem(updated, false);
        }
    }

    private static ItemStack migrate(ItemStack stack) {
        if (!stack.is(Items.COMMAND_BLOCK)
                && !stack.is(Items.REPEATING_COMMAND_BLOCK)
                && !stack.is(Items.CHAIN_COMMAND_BLOCK)) return stack;
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (modelData == null || modelData.strings().isEmpty()) return stack;
        FakeItem item = FakeItems.ID_MAP.get(modelData.strings().getFirst());
        if (item == null || item.baseItem() != Items.HEART_OF_THE_SEA) return stack;
        return stack.transmuteCopy(Items.HEART_OF_THE_SEA, stack.getCount());
    }
}
