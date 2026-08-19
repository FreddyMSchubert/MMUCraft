package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.BrushableBlockEntityAccessor;

import java.util.Map;

public final class FishingModifiers {
    public static final double DEFAULT_ITEM_CHANCE = 0.35;
    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_HEIGHT = 4;

    private static final Map<String, Modifier> MODIFIERS = Map.of(
            "item-magnet", new Modifier(0.50, 20),
            "golden-item-magnet", new Modifier(0.70, 20),
            "worms", new Modifier(0.15, 20),
            "golden-worms", new Modifier(0.0, 20)
    );

    private FishingModifiers() {
    }

    public static double onCast(Player player) {
        seedNearbyBrushables(player);
        ItemStack stack = player.getOffhandItem();
        Modifier modifier = MODIFIERS.entrySet().stream()
                .filter(entry -> FakeItems.isSpecificFakeItem(stack, entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (modifier == null) {
            return DEFAULT_ITEM_CHANCE;
        }

        if (!player.hasInfiniteMaterials()) {
            useOnce(player, stack, modifier.uses());
        }
        return modifier.itemChance();
    }

    private static void seedNearbyBrushables(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        RandomSource random = level.getRandom();
        BlockPos origin = player.blockPosition();
        BlockPos sand = null;
        BlockPos gravel = null;
        int sandSeen = 0;
        int gravelSeen = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS)
        )) {
            Block block = level.getBlockState(pos).getBlock();
            if (block == Blocks.SAND && random.nextInt(++sandSeen) == 0) {
                sand = pos.immutable();
            } else if (block == Blocks.GRAVEL && random.nextInt(++gravelSeen) == 0) {
                gravel = pos.immutable();
            }
        }

        if (sand != null) {
            placeBrushable(level, sand, Blocks.SUSPICIOUS_SAND,
                    random.nextFloat() < 0.9F ? "worms" : "golden-worms");
        }
        if (gravel != null) {
            placeBrushable(level, gravel, Blocks.SUSPICIOUS_GRAVEL,
                    random.nextFloat() < 0.9F ? "item-magnet" : "golden-item-magnet");
        }
    }

    private static void placeBrushable(ServerLevel level, BlockPos pos, Block block, String fakeItemId) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable) {
            ((BrushableBlockEntityAccessor) brushable).mainmod$setItem(FakeItems.createFakeItemStack(fakeItemId, 1));
            brushable.setChanged();
        }
    }

    private static void useOnce(Player player, ItemStack stack, int maxUses) {
        if (!stack.isDamageableItem()) {
            if (stack.getCount() > 1) {
                ItemStack remainder = stack.copyWithCount(stack.getCount() - 1);
                stack.setCount(1);
                if (!player.getInventory().add(remainder) && !remainder.isEmpty()) {
                    player.drop(remainder, false);
                }
            }

            // Vanilla forbids damageable stacks, so the active item splits off only when its durability begins.
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
            stack.set(DataComponents.MAX_DAMAGE, maxUses);
            stack.set(DataComponents.DAMAGE, 0);
        }

        int nextDamage = stack.getDamageValue() + 1;
        if (nextDamage >= maxUses) {
            player.onEquippedItemBroken(stack.getItem(), EquipmentSlot.OFFHAND);
            stack.shrink(1);
        } else {
            stack.setDamageValue(nextDamage);
        }
    }

    private record Modifier(double itemChance, int uses) {
    }
}
