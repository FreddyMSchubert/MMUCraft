package dev.freddy.killshift;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.ArrayList;
import java.util.List;

final class MobFood {
    private MobFood() { }

    static InteractionResult onUseItem(Player user, Level level, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.form.type() == EntityTypes.PLAYER) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        if (AbilitySlot.trigger(stack)) return InteractionResult.PASS;
        boolean allowed = canEat(state, stack);
        if (!stack.has(DataComponents.FOOD) && !allowed) return InteractionResult.PASS;
        if (!allowed) {
            explainDiet(player, state, stack);
            return InteractionResult.FAIL;
        }
        if (stack.has(DataComponents.FOOD)) return InteractionResult.PASS;

        stack.consume(1, player);
        player.getFoodData().eat(2, 0.2F);
        return InteractionResult.SUCCESS_SERVER.withoutItem();
    }

    static InteractionResult onUseBlock(Player user, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.form.type() == EntityTypes.PLAYER) return InteractionResult.PASS;
        if (AbilitySlot.trigger(player.getItemInHand(hand))) return InteractionResult.PASS;
        var block = level.getBlockState(hit.getBlockPos()).getBlock();
        if (block instanceof CakeBlock || block instanceof CandleCakeBlock) {
            explainDiet(player, state, new ItemStack(Items.CAKE));
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static void explainDiet(ServerPlayer player, ShapeState state, ItemStack attempted) {
        List<Component> foods = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (canEat(state, stack)) foods.add(stack.getHoverName());
        }
        MutableComponent message = Component.literal("You can't eat ").append(attempted.getHoverName())
                .append(Component.literal(" while transformed as ")).append(state.form.type().getDescription())
                .append(Component.literal(". This form can only eat "));
        for (int i = 0; i < foods.size(); i++) {
            if (i > 0) message.append(Component.literal(i == foods.size() - 1 ? " or " : ", "));
            message.append(foods.get(i));
        }
        player.sendSystemMessage(message.append(Component.literal(".")));
    }

    private static boolean canEat(ShapeState state, ItemStack stack) {
        var type = state.form.type();
        if (stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SOUP)) return true;
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
