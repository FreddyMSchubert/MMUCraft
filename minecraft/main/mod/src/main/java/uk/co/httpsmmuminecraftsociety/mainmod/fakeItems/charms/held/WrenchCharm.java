package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackBlockCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseOnBlockCallbackCharm;

import java.util.List;

public final class WrenchCharm implements Charm, UseOnBlockCallbackCharm, AttackBlockCallbackCharm {
    private static final List<Property<?>> ALLOWED_PROPERTIES = List.of(
            BlockStateProperties.FACING,
            BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.FACING_HOPPER,
            BlockStateProperties.VERTICAL_DIRECTION,
            BlockStateProperties.AXIS,
            BlockStateProperties.HORIZONTAL_AXIS,
            BlockStateProperties.ORIENTATION,
            BlockStateProperties.ROTATION_16,
            BlockStateProperties.ATTACH_FACE,
            BlockStateProperties.ATTACHED,
            BlockStateProperties.HALF,
            BlockStateProperties.STAIRS_SHAPE,
            BlockStateProperties.DOOR_HINGE,
            BlockStateProperties.BELL_ATTACHMENT,
            BlockStateProperties.EAST_WALL,
            BlockStateProperties.NORTH_WALL,
            BlockStateProperties.SOUTH_WALL,
            BlockStateProperties.WEST_WALL,
            BlockStateProperties.RAIL_SHAPE,
            BlockStateProperties.RAIL_SHAPE_STRAIGHT,
            BlockStateProperties.SIDE_CHAIN_PART,
            BlockStateProperties.BAMBOO_LEAVES,
            BlockStateProperties.TILT,
            BlockStateProperties.COPPER_GOLEM_POSE,
            BlockStateProperties.CRACKED,
            BlockStateProperties.BLOOM,
            BlockStateProperties.NOTEBLOCK_INSTRUMENT,
            BlockStateProperties.NOTE,
            BlockStateProperties.MODE_COMPARATOR,
            BlockStateProperties.DELAY,
            BlockStateProperties.INVERTED,
            BlockStateProperties.LOCKED,
            BlockStateProperties.OCCUPIED,
            BlockStateProperties.OPEN,
            BlockStateProperties.BOTTOM,
            BlockStateProperties.IN_WALL,
            BlockStateProperties.SHORT,
            BlockStateProperties.SIGNAL_FIRE,
            BlockStateProperties.SNOWY,
            BlockStateProperties.UP,
            BlockStateProperties.DOWN,
            BlockStateProperties.NORTH,
            BlockStateProperties.EAST,
            BlockStateProperties.SOUTH,
            BlockStateProperties.WEST
    );

    static {
        if (ALLOWED_PROPERTIES.stream().distinct().count() != ALLOWED_PROPERTIES.size()) {
            throw new IllegalStateException("Wrench property allowlist contains duplicates");
        }
    }

    @Override
    public InteractionResult onUseOnBlock(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockHitResult hit,
            int charmLevel
    ) {
        if (stack != player.getItemInHand(hand)) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        if (!canChange(player, level, stack, pos, hit.getDirection())) return InteractionResult.FAIL;

        BlockState state = level.getBlockState(pos);
        List<Property<?>> properties = allowedProperties(state);
        if (properties.isEmpty()) return showNoProperties(player);

        Property<?> selected = player.isSecondaryUseActive()
                ? selectedProperty(stack, state, properties)
                : properties.getFirst();
        BlockState changed = cycle(state, selected);
        if (!level.setBlock(pos, changed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
            return InteractionResult.FAIL;
        }

        showProperties(player, changed, properties, selected);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onAttackBlock(
            ItemStack stack,
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockPos pos,
            Direction direction,
            int charmLevel
    ) {
        if (stack != player.getItemInHand(hand) || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!canChange(player, level, stack, pos, direction)) return InteractionResult.FAIL;

        BlockState state = level.getBlockState(pos);
        List<Property<?>> properties = allowedProperties(state);
        if (properties.isEmpty()) return showNoProperties(player);

        Property<?> selected = selectedProperty(stack, state, properties);
        selected = properties.get((properties.indexOf(selected) + 1) % properties.size());

        DebugStickState selection = stack.getOrDefault(DataComponents.DEBUG_STICK_STATE, DebugStickState.EMPTY);
        stack.set(DataComponents.DEBUG_STICK_STATE, selection.withProperty(state.typeHolder(), selected));
        showProperties(player, state, properties, selected);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean canChange(
            ServerPlayer player,
            ServerLevel level,
            ItemStack stack,
            BlockPos pos,
            Direction direction
    ) {
        return ClaimsManager.canAccess(player, level, pos) && player.mayUseItemAt(pos, direction, stack);
    }

    private static List<Property<?>> allowedProperties(BlockState state) {
        return ALLOWED_PROPERTIES.stream().filter(state::hasProperty).toList();
    }

    private static Property<?> selectedProperty(
            ItemStack stack,
            BlockState state,
            List<Property<?>> properties
    ) {
        Property<?> selected = stack.getOrDefault(DataComponents.DEBUG_STICK_STATE, DebugStickState.EMPTY)
                .properties()
                .get(state.typeHolder());
        return properties.contains(selected) ? selected : properties.getFirst();
    }

    private static InteractionResult showNoProperties(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("No safe properties").withStyle(ChatFormatting.RED));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void showProperties(
            ServerPlayer player,
            BlockState state,
            List<Property<?>> properties,
            Property<?> selected
    ) {
        MutableComponent message = Component.empty();
        for (int i = 0; i < properties.size(); i++) {
            if (i > 0) message.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            Property<?> property = properties.get(i);
            MutableComponent label = Component.literal(propertyValue(state, property));
            if (property == selected) label.withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
            message.append(label);
        }
        player.sendOverlayMessage(message);
    }

    private static BlockState cycle(BlockState state, Property<?> property) {
        return cycleTyped(state, property);
    }

    private static <T extends Comparable<T>> BlockState cycleTyped(BlockState state, Property<T> property) {
        return state.cycle(property);
    }

    private static String propertyValue(BlockState state, Property<?> property) {
        return propertyValueTyped(state, property);
    }

    private static <T extends Comparable<T>> String propertyValueTyped(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }
}
