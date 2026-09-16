package uk.co.httpsmmuminecraftsociety.mainmod.inventoryview;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

final class InventoryViewMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;
    private static final int INVENTORY_ROWS = 5;
    private static final int[] EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD.getIndex(Inventory.INVENTORY_SIZE),
            EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE),
            EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE),
            EquipmentSlot.FEET.getIndex(Inventory.INVENTORY_SIZE),
            Inventory.SLOT_OFFHAND
    };
    private static final int INVENTORY_TARGET_SLOTS = Inventory.INVENTORY_SIZE + EQUIPMENT_SLOTS.length;
    private static final int INVENTORY_MENU_SLOTS = INVENTORY_ROWS * COLUMNS;
    private static final int ENDER_CHEST_SLOTS = 3 * COLUMNS;
    private static final Set<InventoryViewMenu> OPEN_MENUS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    enum Kind {
        INVENTORY,
        ENDER_CHEST
    }

    private final InventoryViewCommands.TargetPlayer target;
    private final ServerPlayer viewer;
    private final Kind kind;
    private final SimpleContainer barriers;
    private boolean released;

    InventoryViewMenu(int containerId, Inventory viewerInventory, InventoryViewCommands.TargetPlayer target, Kind kind) {
        super(menuType(kind), containerId);
        this.target = target;
        this.viewer = (ServerPlayer) viewerInventory.player;
        this.kind = kind;
        this.barriers = kind == Kind.INVENTORY ? createBarriers() : null;

        if (kind == Kind.INVENTORY) {
            addInventorySlots(target.player().getInventory());
            addBarrierSlots();
            addStandardInventorySlots(viewerInventory, 8, 18 + INVENTORY_ROWS * 18 + 13);
        } else {
            addContainerSlots(target.player().getEnderChestInventory(), 3);
            addStandardInventorySlots(viewerInventory, 8, 18 + 3 * 18 + 13);
        }
        OPEN_MENUS.add(this);
    }

    @SuppressWarnings("unchecked")
    private static MenuType<InventoryViewMenu> menuType(Kind kind) {
        return (MenuType<InventoryViewMenu>) (MenuType<?>) (kind == Kind.INVENTORY
                ? MenuType.GENERIC_9x5
                : MenuType.GENERIC_9x3);
    }

    private static SimpleContainer createBarriers() {
        SimpleContainer container = new SimpleContainer(INVENTORY_MENU_SLOTS - INVENTORY_TARGET_SLOTS);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(Items.BARRIER));
        }
        return container;
    }

    private void addInventorySlots(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            addSlot(new Slot(inventory, slot, 8 + slot % COLUMNS * 18, 18 + slot / COLUMNS * 18));
        }
        for (int menuSlot = 0; menuSlot < EQUIPMENT_SLOTS.length; menuSlot++) {
            addSlot(new Slot(inventory, EQUIPMENT_SLOTS[menuSlot], 8 + menuSlot * 18, 18 + 4 * 18));
        }
    }

    private void addBarrierSlots() {
        for (int slot = 0; slot < barriers.getContainerSize(); slot++) {
            addSlot(new LockedSlot(barriers, slot, 8 + (slot + 5) * 18, 18 + 4 * 18));
        }
    }

    private void addContainerSlots(Container container, int rows) {
        checkContainerSize(container, rows * COLUMNS);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = column + row * COLUMNS;
                addSlot(new Slot(container, slot, 8 + column * 18, 18 + row * 18));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !player.isRemoved();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        target.changed();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        int viewedSlots = kind == Kind.INVENTORY ? INVENTORY_MENU_SLOTS : ENDER_CHEST_SLOTS;
        int editableSlots = kind == Kind.INVENTORY ? INVENTORY_TARGET_SLOTS : ENDER_CHEST_SLOTS;

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            boolean moved = slotIndex < viewedSlots
                    ? moveItemStackTo(stack, viewedSlots, slots.size(), true)
                    : moveItemStackTo(stack, 0, editableSlots, false);
            if (!moved) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        release();
    }

    private void release() {
        if (released) return;
        released = true;
        OPEN_MENUS.remove(this);
        target.release();
    }

    static void closeMenusFor(UUID targetId) {
        OPEN_MENUS.stream()
                .filter(menu -> menu.target.player().getUUID().equals(targetId))
                .toList()
                .forEach(menu -> menu.viewer.closeContainer());
    }

    private static final class LockedSlot extends Slot {
        private LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean allowModification(Player player) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int count) {
            return stack;
        }

        @Override
        public void setByPlayer(ItemStack stack) {}

        @Override
        public void set(ItemStack stack) {}
    }
}
