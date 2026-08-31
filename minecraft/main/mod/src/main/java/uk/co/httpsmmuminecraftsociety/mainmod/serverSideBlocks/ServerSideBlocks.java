package uk.co.httpsmmuminecraftsociety.mainmod.serverSideBlocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public final class ServerSideBlocks {
    private static final float INSTANT_DESTROY_PROGRESS = 1.0F;
    private static final Map<Block, Definition> SERVER_SIDE_BLOCKS = Map.of(
            Blocks.TEST_BLOCK, new Definition("Alien Debris", Rarity.RARE, false),
            Blocks.TEST_INSTANCE_BLOCK, new Definition("Enderite Block", Rarity.EPIC, true)
    );

    private ServerSideBlocks() {}

    public static boolean isServerSideBlock(Block block) {
        return SERVER_SIDE_BLOCKS.containsKey(block);
    }

    public static boolean isServerSideBlock(BlockState state) {
        return isServerSideBlock(state.getBlock());
    }

    public static ItemStack createDrop(BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        applyItemComponents(stack, state.getBlock());
        return stack;
    }

    public static float getDestroyProgress(BlockState state) {
        if (!isServerSideBlock(state)) {
            return 0.0F;
        }

        return INSTANT_DESTROY_PROGRESS;
    }

    private static void applyItemComponents(ItemStack stack, Block block) {
        Definition definition = SERVER_SIDE_BLOCKS.get(block);
        if (definition == null) {
            return;
        }

        stack.set(DataComponents.ITEM_NAME, Component.literal(definition.itemName()));
        stack.set(DataComponents.RARITY, definition.rarity());
        if (definition.fireproof()) {
            stack.set(DataComponents.DAMAGE_RESISTANT, Blocks.NETHERITE_BLOCK.asItem().components().get(DataComponents.DAMAGE_RESISTANT));
        }
    }

    private record Definition(String itemName, Rarity rarity, boolean fireproof) {}
}
