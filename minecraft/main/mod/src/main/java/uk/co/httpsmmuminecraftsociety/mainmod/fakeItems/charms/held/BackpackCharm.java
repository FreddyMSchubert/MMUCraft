package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

public class BackpackCharm implements Charm, UseCallbackCharm {
    private static final int SLOTS_PER_ROW = 9;

    private final int rows;

    public BackpackCharm(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Backpack rows must be between 1 and 6, got " + rows);
        }
        this.rows = rows;
    }

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new BackpackMenu(id, inventory, stack, rows),
                stack.getHoverName()
        ));
        return InteractionResult.SUCCESS;
    }

    public enum Tier {
        LEATHER("charm-leather-backpack", 44, 1),
        INGOT("charm-ingot-backpack", 45, 2),
        MAGIC("charm-magic-backpack", 46, 3),
        BEJEWELED("charm-bejeweled-backpack", 47, 4),
        WITHERED("charm-withered-backpack", 48, 5),
        ENDLESS("charm-endless-backpack", 49, 6);

        private final String fakeItemId;
        private final int charmId;
        private final int rows;

        Tier(String fakeItemId, int charmId, int rows) {
            this.fakeItemId = fakeItemId;
            this.charmId = charmId;
            this.rows = rows;
        }

        public String fakeItemId() {
            return fakeItemId;
        }

        public int charmId() {
            return charmId;
        }

        public int rows() {
            return rows;
        }
    }

    public static int getBackpackRows(ItemStack stack) {
        StoredCharmData storedCharm = CharmStackData.getSingleStoredCharm(stack).orElse(null);
        if (storedCharm == null) {
            return 0;
        }

        for (Tier tier : Tier.values()) {
            if (tier.charmId() == storedCharm.charmId()) {
                return tier.rows();
            }
        }

        return 0;
    }

    public static boolean isBackpack(ItemStack stack) {
        return getBackpackRows(stack) > 0;
    }

    public static boolean containsBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isBackpack(stack)) {
            return true;
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null && containerContents.nonEmptyItemCopyStream().anyMatch(BackpackCharm::containsBackpack)) {
            return true;
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundleContents != null && bundleContents.itemCopyStream().anyMatch(BackpackCharm::containsBackpack);
    }

    public static boolean containsItemDisallowedInBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isBackpack(stack) || isBundle(stack) || isShulkerBox(stack)) {
            return true;
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null && containerContents.nonEmptyItemCopyStream().anyMatch(BackpackCharm::containsItemDisallowedInBackpack)) {
            return true;
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundleContents != null && bundleContents.itemCopyStream().anyMatch(BackpackCharm::containsItemDisallowedInBackpack);
    }

    private static boolean isBundle(ItemStack stack) {
        return stack.getItem() instanceof BundleItem;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean isTier(ItemStack stack, Tier tier) {
        return isBackpack(stack) && FakeItems.isSpecificFakeItem(stack, tier.fakeItemId());
    }

    private static void copyContents(ItemStack source, ItemStack target) {
        ItemContainerContents contents = source.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        target.set(DataComponents.CONTAINER, contents);
    }

    public static ItemStack createTierStack(Tier tier, ItemStack sourceBackpack) {
        ItemStack result = FakeItems.createFakeItemStack(tier.fakeItemId(), 1);
        if (!sourceBackpack.isEmpty()) {
            copyContents(sourceBackpack, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static MenuType<BackpackMenu> menuTypeForRows(int rows) {
        return switch (rows) {
            case 1 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x1;
            case 2 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x2;
            case 3 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x3;
            case 4 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x4;
            case 5 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x5;
            case 6 -> (MenuType<BackpackMenu>) (MenuType<?>) MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Backpack rows must be between 1 and 6, got " + rows);
        };
    }

    private static class BackpackContainer extends SimpleContainer {
        private final ItemStack backpackStack;

        BackpackContainer(ItemStack backpackStack, int rows) {
            super(rows * SLOTS_PER_ROW);
            this.backpackStack = backpackStack;
            backpackStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && !player.isRemoved() && isBackpack(backpackStack);
        }

        void save() {
            if (isEmpty()) {
                backpackStack.remove(DataComponents.CONTAINER);
                return;
            }

            backpackStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }

    private static class BackpackSlot extends Slot {
        BackpackSlot(BackpackContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !containsItemDisallowedInBackpack(stack);
        }
    }

    private static class BackpackMenu extends AbstractContainerMenu {
        private final BackpackContainer container;
        private final int rows;

        BackpackMenu(int containerId, Inventory inventory, ItemStack backpackStack, int rows) {
            super(menuTypeForRows(rows), containerId);
            this.rows = rows;
            this.container = new BackpackContainer(backpackStack, rows);

            checkContainerSize(this.container, rows * SLOTS_PER_ROW);
            this.container.startOpen(inventory.player);
            addBackpackSlots();
            addStandardInventorySlots(inventory, 8, 18 + rows * 18 + 13);
        }

        private void addBackpackSlots() {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < SLOTS_PER_ROW; column++) {
                    addSlot(new BackpackSlot(
                            container,
                            column + row * SLOTS_PER_ROW,
                            8 + column * 18,
                            18 + row * 18
                    ));
                }
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return container.stillValid(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            ItemStack moved = ItemStack.EMPTY;
            Slot slot = this.slots.get(slotIndex);

            if (slot != null && slot.hasItem()) {
                ItemStack slotStack = slot.getItem();
                moved = slotStack.copy();
                int storageEnd = rows * SLOTS_PER_ROW;

                if (slotIndex < storageEnd) {
                    if (!moveItemStackTo(slotStack, storageEnd, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(slotStack, 0, storageEnd, false)) {
                    return ItemStack.EMPTY;
                }

                if (slotStack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }

            return moved;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            container.stopOpen(player);
            container.save();
        }
    }
}
