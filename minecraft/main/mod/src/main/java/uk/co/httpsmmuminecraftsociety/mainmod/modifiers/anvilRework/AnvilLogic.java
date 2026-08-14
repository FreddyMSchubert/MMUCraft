package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes.AnvilRecipe;

public final class AnvilLogic
{
    public record Outcome(
            int xpLevelsConsumed,
            ItemStack leftRemainder,
            ItemStack rightRemainder,
            ItemStack result
    ) {
        public Outcome
        {
            xpLevelsConsumed = Math.min(xpLevelsConsumed, 30); // never exceed xp limit
            if (!result.isEmpty())
                result.set(DataComponents.REPAIR_COST, 0);
        }

        public static final Outcome EMPTY = new Outcome(
                0,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    public static Outcome compute(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name) {
        if (left.isEmpty()) {
            return Outcome.EMPTY;
        }

        AnvilRecipe recipe = AnvilRecipe.getFirstMatching(left, right);
        if (recipe == null) return Outcome.EMPTY;
        return recipe.apply(player, left, right, name);
    }

    // anvil repairing via clicking with iron ingots / iron blocks
    public static @Nullable InteractionResult onUseItemOn(UseOnContext ctx) {
        if (!ctx.getPlayer().level().getBlockState(ctx.getClickedPos()).is(BlockTags.ANVIL)) return null;
        if (ctx.getItemInHand().getItem() != Items.IRON_BLOCK &&
                !(ctx.getItemInHand().getItem() == Items.IRON_INGOT && Math.random() < (1f / 9f))) return null;

        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(ctx.getClickedPos());

        BlockState repairedState = getRepairedAnvilState(state);
        if (repairedState == null) return null;

        if (!level.isClientSide()) {
            level.setBlock(ctx.getClickedPos(), repairedState, 2);
            level.levelEvent(1030, ctx.getClickedPos(), 0); // anvil use sound/event

            if (ctx.getPlayer() == null || !ctx.getPlayer().hasInfiniteMaterials()) {
                ctx.getItemInHand().shrink(1);
            }

            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.SUCCESS;
    }
    private static @Nullable BlockState getRepairedAnvilState(BlockState state) {
        if (state.is(Blocks.DAMAGED_ANVIL)) {
            return Blocks.CHIPPED_ANVIL
                    .defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        }

        if (state.is(Blocks.CHIPPED_ANVIL)) {
            return Blocks.ANVIL
                    .defaultBlockState()
                    .setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING));
        }

        return null; // already fully repaired or not an anvil
    }
}
