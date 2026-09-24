package dev.freddy.killshift;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.phys.BlockHitResult;

final class MobFood {
    private MobFood() { }

    static InteractionResult onUseItem(Player user, Level level, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.form.type() == EntityTypes.PLAYER) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        boolean allowed = canEat(state, stack);
        if (!stack.has(DataComponents.FOOD) && !allowed) return InteractionResult.PASS;
        if (!allowed) return InteractionResult.FAIL;
        if (stack.has(DataComponents.FOOD)) return InteractionResult.PASS;

        stack.consume(1, player);
        player.getFoodData().eat(2, 0.2F);
        return InteractionResult.SUCCESS_SERVER.withoutItem();
    }

    static InteractionResult onUseBlock(Player user, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.form.type() == EntityTypes.PLAYER) return InteractionResult.PASS;
        var block = level.getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof CakeBlock || block instanceof CandleCakeBlock
                ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    private static boolean canEat(ShapeState state, ItemStack stack) {
        var type = state.form.type();
        if (type == EntityTypes.PANDA) return stack.is(Items.BAMBOO);
        if (type == EntityTypes.BEE) return false;
        if (type == EntityTypes.VILLAGER) {
            return stack.is(Items.BREAD) || stack.is(Items.CARROT)
                    || stack.is(Items.POTATO) || stack.is(Items.BEETROOT);
        }
        if (type == EntityTypes.DOLPHIN) {
            return stack.is(Items.COD) || stack.is(Items.SALMON);
        }
        if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.DROWNED
                || type == EntityTypes.ZOMBIE_VILLAGER || type == EntityTypes.ZOMBIE_HORSE
                || type == EntityTypes.ZOMBIE_NAUTILUS) {
            return stack.is(Items.ROTTEN_FLESH);
        }
        return state.view instanceof Animal animal && animal.isFood(stack);
    }
}
