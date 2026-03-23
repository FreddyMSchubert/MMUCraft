package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class CraftingStaffCharm implements Charm, UseCallbackCharm
{
    @Override
    public ItemStack onUse(ItemStack stack, ServerPlayer player, ServerLevel level) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PortableCraftingMenu(id, inv),
                Component.literal(" Crafting ")
        ));
        return stack;
    }

    private static class PortableCraftingMenu extends AbstractCraftingMenu
    {
        private static final int CRAFTING_GRID_WIDTH = 3;
        private static final int CRAFTING_GRID_HEIGHT = 3;

        public static final int RESULT_SLOT = 0;
        private static final int CRAFT_SLOT_START = 1;
        private static final int CRAFT_SLOT_END = 10;
        private static final int INV_SLOT_START = 10;
        private static final int INV_SLOT_END = 37;
        private static final int USE_ROW_SLOT_START = 37;
        private static final int USE_ROW_SLOT_END = 46;

        private final Player player;
        private boolean placingRecipe;

        public PortableCraftingMenu(int containerId, Inventory inventory) {
            super(MenuType.CRAFTING, containerId, CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT);
            this.player = inventory.player;
            this.addResultSlot(this.player, 124, 35);
            this.addCraftingGridSlots(30, 17);
            this.addStandardInventorySlots(inventory, 8, 84);
        }

        protected static void slotChangedCraftingGrid(
                PortableCraftingMenu menu,
                ServerLevel serverLevel,
                Player player,
                CraftingContainer craftingContainer,
                ResultContainer resultContainer,
                @Nullable RecipeHolder<CraftingRecipe> recipeHolder
        ) {
            CraftingInput craftingInput = craftingContainer.asCraftInput();
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ItemStack result = ItemStack.EMPTY;

            Optional<RecipeHolder<CraftingRecipe>> optional = serverLevel.getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftingInput, serverLevel, recipeHolder);

            if (optional.isPresent()) {
                RecipeHolder<CraftingRecipe> foundHolder = optional.get();
                CraftingRecipe recipe = foundHolder.value();

                if (resultContainer.setRecipeUsed(serverPlayer, foundHolder)) {
                    ItemStack assembled = recipe.assemble(craftingInput, serverLevel.registryAccess());
                    if (assembled.isItemEnabled(serverLevel.enabledFeatures())) {
                        result = assembled;
                    }
                }
            }

            resultContainer.setItem(0, result);
            menu.setRemoteSlot(0, result);
            serverPlayer.connection.send(
                    new ClientboundContainerSetSlotPacket(
                            menu.containerId,
                            menu.incrementStateId(),
                            0,
                            result
                    )
            );
        }

        @Override
        public void slotsChanged(Container container) {
            if (!this.placingRecipe && this.player.level() instanceof ServerLevel serverLevel) {
                slotChangedCraftingGrid(this, serverLevel, this.player, this.craftSlots, this.resultSlots, null);
            }
        }

        @Override
        public void beginPlacingRecipe() {
            this.placingRecipe = true;
        }

        @Override
        public void finishPlacingRecipe(ServerLevel serverLevel, RecipeHolder<CraftingRecipe> recipeHolder) {
            this.placingRecipe = false;
            slotChangedCraftingGrid(this, serverLevel, this.player, this.craftSlots, this.resultSlots, recipeHolder);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            this.clearContainer(player, this.craftSlots);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && !player.isRemoved();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            ItemStack moved = ItemStack.EMPTY;
            Slot slot = this.slots.get(slotIndex);

            if (slot != null && slot.hasItem()) {
                ItemStack slotStack = slot.getItem();
                moved = slotStack.copy();

                if (slotIndex == RESULT_SLOT) {
                    slotStack.getItem().onCraftedBy(slotStack, player);
                    if (!this.moveItemStackTo(slotStack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                        return ItemStack.EMPTY;
                    }
                    slot.onQuickCraft(slotStack, moved);
                } else if (slotIndex >= INV_SLOT_START && slotIndex < USE_ROW_SLOT_END) {
                    if (!this.moveItemStackTo(slotStack, CRAFT_SLOT_START, CRAFT_SLOT_END, false)) {
                        if (slotIndex < INV_SLOT_END) {
                            if (!this.moveItemStackTo(slotStack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (!this.moveItemStackTo(slotStack, INV_SLOT_START, INV_SLOT_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else if (!this.moveItemStackTo(slotStack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }

                if (slotStack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }

                if (slotStack.getCount() == moved.getCount()) {
                    return ItemStack.EMPTY;
                }

                slot.onTake(player, slotStack);

                if (slotIndex == RESULT_SLOT) {
                    player.drop(slotStack, false);
                }
            }

            return moved;
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
            return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
        }

        @Override
        public Slot getResultSlot() {
            return this.slots.get(0);
        }

        @Override
        public List<Slot> getInputGridSlots() {
            return this.slots.subList(1, 10);
        }

        @Override
        public RecipeBookType getRecipeBookType() {
            return RecipeBookType.CRAFTING;
        }

        @Override
        protected Player owner() {
            return this.player;
        }
    }

}
