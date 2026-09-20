package uk.co.httpsmmuminecraftsociety.mainmod.instancedloot;

import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class InstancedLootMenus {
    private InstancedLootMenus() {
    }

    public static AbstractContainerMenu blockEntityMenu(RandomizableContainerBlockEntity blockEntity, int syncId, Inventory inventory, Player player) {
        if (blockEntity instanceof ChestBlockEntity chest) {
            return singleChestMenu(chest, syncId, inventory, player);
        }

        if (blockEntity instanceof BarrelBlockEntity barrel) {
            return barrelMenu(barrel, syncId, inventory, player);
        }

        return null;
    }

    public static AbstractContainerMenu singleChestMenu(ChestBlockEntity chest, int syncId, Inventory inventory, Player player) {
        PlayerLootContainer loot = openBlockContainer(chest, player, 27, List.of(chest));
        return loot == null ? null : ChestMenu.threeRows(syncId, inventory, loot);
    }

    public static AbstractContainerMenu barrelMenu(BarrelBlockEntity barrel, int syncId, Inventory inventory, Player player) {
        PlayerLootContainer loot = openBlockContainer(barrel, player, 27, List.of(barrel));
        return loot == null ? null : ChestMenu.threeRows(syncId, inventory, loot);
    }

    public static AbstractContainerMenu minecartMenu(AbstractMinecartContainer minecart, int syncId, Inventory inventory, Player player) {
        if (!(minecart instanceof MinecartChest)) {
            return null;
        }

        PlayerLootContainer loot = openMinecartContainer(minecart, player);
        return loot == null ? null : ChestMenu.threeRows(syncId, inventory, loot);
    }

    public static MenuProvider chestMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return null;
        }

        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity current)) {
            return null;
        }

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return isLootCandidate(current) ? singleChestProvider(current) : null;
        }

        BlockPos otherPos = ChestBlock.getConnectedBlockPos(pos, state);
        if (!(level.getBlockEntity(otherPos) instanceof ChestBlockEntity other)) {
            return isLootCandidate(current) ? singleChestProvider(current) : null;
        }

        boolean currentLoot = isLootCandidate(current);
        boolean otherLoot = isLootCandidate(other);
        if (!currentLoot && !otherLoot) {
            return null;
        }

        if (currentLoot != otherLoot) {
            return currentLoot ? singleChestProvider(current) : current;
        }

        ChestBlockEntity first = type == ChestType.RIGHT ? current : other;
        ChestBlockEntity second = type == ChestType.RIGHT ? other : current;
        return doubleChestProvider(first, second);
    }

    public static BlockState preventPlacementConnection(BlockPlaceContext context, BlockState state) {
        if (state == null || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return state;
        }

        BlockPos connectedPos = ChestBlock.getConnectedBlockPos(context.getClickedPos(), state);
        boolean newLoot = context.getItemInHand().has(DataComponents.CONTAINER_LOOT);
        boolean neighborLoot = isLootChest(context.getLevel(), connectedPos);
        return newLoot == neighborLoot ? state : state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
    }

    public static BlockState preventMismatchedConnection(LevelReader level, BlockPos pos, BlockState state, BlockPos neighborPos) {
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return state;
        }

        boolean currentLoot = isLootChest(level, pos);
        boolean neighborLoot = isLootChest(level, neighborPos);
        return currentLoot == neighborLoot ? state : state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
    }

    public static boolean isLootChest(LevelReader level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChestBlockEntity chest && isLootCandidate(chest);
    }

    public static boolean isLootCandidate(RandomizableContainerBlockEntity blockEntity) {
        return blockEntity instanceof InstancedLootData data && (data.pil$isInstancedLoot() || blockEntity.getLootTable() != null);
    }

    public static boolean isLootCandidate(AbstractMinecartContainer minecart) {
        return minecart instanceof InstancedLootData data && (data.pil$isInstancedLoot() || minecart.getContainerLootTable() != null);
    }

    public static void preventVanillaBlockUnpack(RandomizableContainerBlockEntity blockEntity, Player player) {
        if (!isLootCandidate(blockEntity)) {
            return;
        }

        if (player == null) {
            ensureBlockInstanced(blockEntity);
        }
    }

    public static void preventVanillaMinecartUnpack(AbstractMinecartContainer minecart, Player player) {
        if (!isLootCandidate(minecart)) {
            return;
        }

        if (player == null) {
            ensureMinecartInstanced(minecart);
        }
    }

    private static MenuProvider singleChestProvider(ChestBlockEntity chest) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return chest.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return singleChestMenu(chest, syncId, inventory, player);
            }
        };
    }

    private static MenuProvider doubleChestProvider(ChestBlockEntity first, ChestBlockEntity second) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                if (first.hasCustomName()) {
                    return first.getDisplayName();
                }
                if (second.hasCustomName()) {
                    return second.getDisplayName();
                }
                return Component.translatable("container.chestDouble");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                PlayerLootContainer loot = openDoubleChestContainer(first, second, player);
                return loot == null ? null : ChestMenu.sixRows(syncId, inventory, loot);
            }
        };
    }

    private static PlayerLootContainer openBlockContainer(RandomizableContainerBlockEntity blockEntity, Player player, int size, List<Container> openDelegates) {
        if (!(blockEntity instanceof InstancedLootData data) || !isLootCandidate(blockEntity) || !blockEntity.canOpen(player)) {
            return null;
        }

        ensureBlockInstanced(blockEntity);
        NonNullList<ItemStack> items = getOrGenerateBlockLoot(blockEntity, data, player, size);
        return new PlayerLootContainer(size, items, openDelegates, blockEntity::stillValid, changed -> {
            data.pil$getPlayerLoot().put(player.getUUID(), copyItems(changed));
            data.pil$markInstancedLootDirty();
        });
    }

    private static PlayerLootContainer openDoubleChestContainer(ChestBlockEntity first, ChestBlockEntity second, Player player) {
        if (!(first instanceof InstancedLootData firstData) || !(second instanceof InstancedLootData secondData)) {
            return null;
        }
        if (!first.canOpen(player) || !second.canOpen(player)) {
            return null;
        }

        ensureBlockInstanced(first);
        ensureBlockInstanced(second);

        NonNullList<ItemStack> firstItems = getOrGenerateBlockLoot(first, firstData, player, 27);
        NonNullList<ItemStack> secondItems = getOrGenerateBlockLoot(second, secondData, player, 27);
        NonNullList<ItemStack> combined = NonNullList.withSize(54, ItemStack.EMPTY);
        for (int i = 0; i < 27; i++) {
            combined.set(i, firstItems.get(i).copy());
            combined.set(i + 27, secondItems.get(i).copy());
        }

        return new PlayerLootContainer(54, combined, List.of(first, second), playerToCheck -> first.stillValid(playerToCheck) && second.stillValid(playerToCheck), changed -> {
            firstData.pil$getPlayerLoot().put(player.getUUID(), copyRange(changed, 0, 27));
            secondData.pil$getPlayerLoot().put(player.getUUID(), copyRange(changed, 27, 27));
            firstData.pil$markInstancedLootDirty();
            secondData.pil$markInstancedLootDirty();
        });
    }

    private static PlayerLootContainer openMinecartContainer(AbstractMinecartContainer minecart, Player player) {
        if (!(minecart instanceof InstancedLootData data) || !isLootCandidate(minecart) || !minecart.stillValid(player)) {
            return null;
        }

        ensureMinecartInstanced(minecart);
        NonNullList<ItemStack> items = getOrGenerateMinecartLoot(minecart, data, player);
        return new PlayerLootContainer(27, items, List.of(minecart), minecart::stillValid, changed -> {
            data.pil$getPlayerLoot().put(player.getUUID(), copyItems(changed));
            data.pil$markInstancedLootDirty();
        });
    }

    private static void ensureBlockInstanced(RandomizableContainerBlockEntity blockEntity) {
        InstancedLootData data = (InstancedLootData) blockEntity;
        if (!data.pil$isInstancedLoot()) {
            data.pil$setInstancedLoot(true);
            data.pil$setInstancedLootTable(blockEntity.getLootTable());
            data.pil$setInstancedLootSeed(blockEntity.getLootTableSeed());
        }

        blockEntity.setLootTable(null);
        blockEntity.setLootTableSeed(0L);
        blockEntity.clearContent();
        data.pil$markInstancedLootDirty();
    }

    private static void ensureMinecartInstanced(AbstractMinecartContainer minecart) {
        InstancedLootData data = (InstancedLootData) minecart;
        if (!data.pil$isInstancedLoot()) {
            data.pil$setInstancedLoot(true);
            data.pil$setInstancedLootTable(minecart.getContainerLootTable());
            data.pil$setInstancedLootSeed(minecart.getContainerLootTableSeed());
        }

        minecart.setContainerLootTable(null);
        minecart.setContainerLootTableSeed(0L);
        minecart.clearItemStacks();
        data.pil$markInstancedLootDirty();
    }

    private static NonNullList<ItemStack> getOrGenerateBlockLoot(RandomizableContainerBlockEntity blockEntity, InstancedLootData data, Player player, int size) {
        UUID playerId = player.getUUID();
        NonNullList<ItemStack> existing = data.pil$getPlayerLoot().get(playerId);
        if (existing != null && existing.size() == size) {
            return copyItems(existing);
        }

        NonNullList<ItemStack> generated = NonNullList.withSize(size, ItemStack.EMPTY);
        fillLoot(generated, blockEntity.getLevel(), Vec3.atCenterOf(blockEntity.getBlockPos()), data, player);
        data.pil$getPlayerLoot().put(playerId, copyItems(generated));
        data.pil$markInstancedLootDirty();
        return generated;
    }

    private static NonNullList<ItemStack> getOrGenerateMinecartLoot(AbstractMinecartContainer minecart, InstancedLootData data, Player player) {
        UUID playerId = player.getUUID();
        NonNullList<ItemStack> existing = data.pil$getPlayerLoot().get(playerId);
        if (existing != null && existing.size() == 27) {
            return copyItems(existing);
        }

        NonNullList<ItemStack> generated = NonNullList.withSize(27, ItemStack.EMPTY);
        fillLoot(generated, minecart.level(), minecart.position(), data, player);
        data.pil$getPlayerLoot().put(playerId, copyItems(generated));
        data.pil$markInstancedLootDirty();
        return generated;
    }

    private static void fillLoot(NonNullList<ItemStack> target, Level level, Vec3 origin, InstancedLootData data, Player player) {
        ResourceKey<LootTable> key = data.pil$getInstancedLootTable();
        if (key == null || !(level instanceof ServerLevel serverLevel) || level.getServer() == null) {
            return;
        }

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(key);
        SimpleContainer generated = new SimpleContainer(target.size());
        LootParams.Builder params = new LootParams.Builder(serverLevel).withParameter(LootContextParams.ORIGIN, origin);
        if (player != null) {
            params.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.GENERATE_LOOT.trigger(serverPlayer, key);
        }

        lootTable.fill(generated, params.create(LootContextParamSets.CHEST), playerSeed(data.pil$getInstancedLootSeed(), player));
        for (int i = 0; i < target.size(); i++) {
            target.set(i, generated.getItem(i).copy());
        }
    }

    private static long playerSeed(long seed, Player player) {
        if (seed == 0L || player == null) {
            return seed;
        }

        UUID uuid = player.getUUID();
        return seed ^ uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32);
    }

    private static NonNullList<ItemStack> copyItems(NonNullList<ItemStack> source) {
        return copyRange(source, 0, source.size());
    }

    private static NonNullList<ItemStack> copyRange(NonNullList<ItemStack> source, int start, int size) {
        NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) {
            copy.set(i, source.get(start + i).copy());
        }
        return copy;
    }

    private static final class PlayerLootContainer extends CompoundContainer implements InstancedLootOpenDelegateView {
        private static final Container EMPTY_IDENTITY = new SimpleContainer(0);

        private final SimpleContainer items;
        private final List<Container> openDelegates;
        private final java.util.function.Predicate<Player> stillValid;
        private final java.util.function.Consumer<NonNullList<ItemStack>> save;
        private boolean loading = true;

        private PlayerLootContainer(int size, NonNullList<ItemStack> items, List<Container> openDelegates, java.util.function.Predicate<Player> stillValid, java.util.function.Consumer<NonNullList<ItemStack>> save) {
            super(openDelegates.isEmpty() ? EMPTY_IDENTITY : openDelegates.get(0), openDelegates.size() > 1 ? openDelegates.get(1) : EMPTY_IDENTITY);
            this.items = new SimpleContainer(size);
            this.openDelegates = List.copyOf(openDelegates);
            this.stillValid = stillValid;
            this.save = save;
            for (int i = 0; i < size; i++) {
                this.items.setItem(i, items.get(i).copy());
            }
            this.loading = false;
        }

        @Override
        public int getContainerSize() {
            return this.items.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return this.items.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.items.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = this.items.removeItem(slot, amount);
            if (!stack.isEmpty()) {
                setChanged();
            }
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = this.items.removeItemNoUpdate(slot);
            setChanged();
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.items.setItem(slot, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            this.items.setChanged();
            if (!this.loading) {
                this.save.accept(copyItems(this.items.getItems()));
                for (Container delegate : this.openDelegates) {
                    delegate.setChanged();
                }
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return this.stillValid.test(player);
        }

        @Override
        public boolean pil$hasOpenDelegate(Container container) {
            return this.openDelegates.contains(container);
        }

        @Override
        public void startOpen(ContainerUser user) {
            for (Container delegate : this.openDelegates) {
                delegate.startOpen(user);
            }
        }

        @Override
        public void stopOpen(ContainerUser user) {
            this.save.accept(copyItems(this.items.getItems()));
            for (Container delegate : this.openDelegates) {
                delegate.stopOpen(user);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return this.items.canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItem(Container target, int slot, ItemStack stack) {
            return this.items.canTakeItem(target, slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.items.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.items.getMaxStackSize(stack);
        }

        @Override
        public void clearContent() {
            this.items.clearContent();
            setChanged();
        }
    }
}
