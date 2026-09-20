package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackBlockCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackEntityCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackSwingCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseOnBlockCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class RedstoneRemoteCharm implements Charm, UseCallbackCharm, UseOnBlockCallbackCharm,
        AttackBlockCallbackCharm, AttackEntityCallbackCharm, AttackSwingCallbackCharm, BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 57;
    private static final String FREQUENCY_KEY = "mainmod_remote_frequency";
    private static final String SENSOR_KEY = "mainmod_remote_sensor";
    private static final String DIMENSION_KEY = "dimension";
    private static final String POSITION_KEY = "position";
    private static final Map<ServerLevel, PriorityQueue<Delivery>> DELIVERIES = new HashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> POWERED_CHESTS = new HashMap<>();

    private record Link(String dimension, BlockPos pos) {}
    private record Delivery(long dueTick, BlockPos pos, int frequency, ItemStack remote,
                            @Nullable ServerPlayer sender) {}
    private enum LinkStatus { READY, UNLINKED, OTHER_DIMENSION, UNLOADED, MISSING }

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        updateLore(stack);
        setModel(stack, modelLit(stack));
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) {
    }

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        if (player.isShiftKeyDown()) tune(stack, player, 1);
        else transmit(stack, level, player.blockPosition(), 0, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onUseOnBlock(ItemStack stack, ServerPlayer player, ServerLevel level,
                                          InteractionHand hand, BlockHitResult hit, int charmLevel) {
        if (stack != player.getItemInHand(hand)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) tune(stack, player, 1);
        else if (isSensor(level.getBlockState(hit.getBlockPos()))) link(stack, player, hit.getBlockPos());
        else transmit(stack, level, player.blockPosition(), 0, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onAttackBlock(ItemStack stack, ServerPlayer player, ServerLevel level,
                                           InteractionHand hand, BlockPos pos, Direction direction, int charmLevel) {
        if (stack != player.getItemInHand(hand)) return InteractionResult.PASS;
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void onAttackSwing(ItemStack stack, ServerPlayer player, ServerLevel level,
                              InteractionHand hand, int charmLevel) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) tune(stack, player, -1);
    }

    @Override
    public InteractionResult onAttackEntity(ItemStack stack, ServerPlayer player, ServerLevel level,
                                            InteractionHand hand, Entity entity, @Nullable EntityHitResult hit,
                                            int charmLevel) {
        return stack == player.getItemInHand(hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
    }

    public static boolean isRemote(ItemStack stack) {
        return CharmStackData.getSingleStoredCharm(stack)
                .filter(charm -> charm.charmId() == CHARM_ID && charm.level() > 0)
                .isPresent();
    }

    public static void clearLink(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.remove(SENSOR_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
        setModel(stack, false);
    }

    private static boolean isSensor(BlockState state) {
        return state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SENSOR);
    }

    private static int frequency(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(FREQUENCY_KEY, 0) & 15;
    }

    private static void tune(ItemStack stack, ServerPlayer player, int step) {
        int next = (frequency(stack) + step + 16) & 15;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(FREQUENCY_KEY, next);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
        setModel(stack, modelLit(stack));
        player.sendSystemMessage(Component.literal("Remote frequency: " + (next + 1)), true);
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static @Nullable Link linkedSensor(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entry = data.getCompound(SENSOR_KEY).orElse(null);
        if (entry == null) return null;
        String dimension = entry.getStringOr(DIMENSION_KEY, "");
        if (dimension.isEmpty() || entry.getLong(POSITION_KEY).isEmpty()) return null;
        return new Link(dimension, BlockPos.of(entry.getLongOr(POSITION_KEY, 0)));
    }

    private static void writeLink(ItemStack stack, Link link) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entry = new CompoundTag();
        entry.putString(DIMENSION_KEY, link.dimension());
        entry.putLong(POSITION_KEY, link.pos().asLong());
        data.put(SENSOR_KEY, entry);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
    }

    private static boolean modelLit(ItemStack stack) {
        List<Boolean> flags = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).flags();
        return !flags.isEmpty() && flags.getFirst();
    }

    private static void setModel(ItemStack stack, boolean lit) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        float selectedFrequency = frequency(stack) + 1;
        if (!data.floats().isEmpty() && data.floats().getFirst() == selectedFrequency
                && !data.flags().isEmpty() && data.flags().getFirst() == lit) return;
        List<Float> floats = new ArrayList<>(data.floats());
        if (floats.isEmpty()) floats.add(selectedFrequency);
        else floats.set(0, selectedFrequency);
        List<Boolean> flags = new ArrayList<>(data.flags());
        if (flags.isEmpty()) flags.add(lit);
        else flags.set(0, lit);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.copyOf(floats), List.copyOf(flags), data.strings(), data.colors()));
    }

    private static void updateLore(ItemStack stack) {
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(CHARM_ID);
        List<Component> lore = new ArrayList<>();
        if (fakeItem != null) {
            CharmItemFeature feature = fakeItem.getFeature(CharmItemFeature.class);
            if (feature != null) lore.addAll(feature.buildTooltip(1));
        }
        if (lore.isEmpty()) lore.addAll(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        int selectedFrequency = frequency(stack) + 1;
        lore.add(Component.literal("Frequency: " + selectedFrequency
                + (selectedFrequency == 16 ? " (unfiltered sensors only)" : ""))
                .withStyle(ChatFormatting.AQUA));
        Link linked = linkedSensor(stack);
        lore.add(Component.literal(linked == null
                ? "Linked sensor: none"
                : "Linked sensor: " + linked.pos().getX() + ", " + linked.pos().getY() + ", "
                        + linked.pos().getZ() + " (" + linked.dimension() + ")")
                .withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        FakeItems.wrapTooltip(stack);
    }

    private static void link(ItemStack stack, ServerPlayer player, BlockPos pos) {
        Link current = linkedSensor(stack);
        Link target = new Link(dimension(player.level()), pos.immutable());
        writeLink(stack, target);
        setModel(stack, true);
        if (target.equals(current)) {
            player.sendSystemMessage(Component.literal("Sensor already linked."), true);
            return;
        }
        player.sendSystemMessage(Component.literal(current == null ? "Sensor linked." : "Sensor link replaced."), true);
    }

    private static LinkStatus linkStatus(ItemStack stack, ServerLevel level) {
        Link link = linkedSensor(stack);
        if (link == null) return LinkStatus.UNLINKED;
        if (!link.dimension().equals(dimension(level))) return LinkStatus.OTHER_DIMENSION;
        BlockPos pos = link.pos();
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return LinkStatus.UNLOADED;
        if (!isSensor(level.getBlockState(pos))
                || !(level.getBlockEntity(pos) instanceof SculkSensorBlockEntity)) return LinkStatus.MISSING;
        return LinkStatus.READY;
    }

    private static void reportUnavailable(@Nullable ServerPlayer player, LinkStatus status) {
        if (player == null) return;
        String message = switch (status) {
            case UNLINKED -> "No sensor linked.";
            case OTHER_DIMENSION -> "Linked sensor is in another dimension.";
            case UNLOADED -> "Linked sensor is not loaded.";
            case MISSING -> "Linked sensor is missing.";
            case READY -> null;
        };
        if (message != null) player.sendSystemMessage(Component.literal(message), true);
    }

    private static void deliveryUnavailable(Delivery delivery, ServerLevel level, LinkStatus status) {
        Link current = linkedSensor(delivery.remote());
        if (current != null && current.dimension().equals(dimension(level))
                && current.pos().equals(delivery.pos())) setModel(delivery.remote(), false);
        reportUnavailable(delivery.sender(), status);
    }

    private static void transmit(ItemStack stack, ServerLevel level, BlockPos source, int extraDelay,
                                 @Nullable ServerPlayer sender) {
        LinkStatus status = linkStatus(stack, level);
        setModel(stack, status == LinkStatus.READY);
        if (status != LinkStatus.READY) {
            reportUnavailable(sender, status);
            return;
        }
        int frequency = frequency(stack);
        float pitch = NoteBlock.getPitchFromNote(frequency);
        level.playSound(null, source, SoundEvents.NOTE_BLOCK_COW_BELL.value(), SoundSource.BLOCKS, 0.8F, pitch);
        int sourceX = source.getX() >> 4;
        int sourceZ = source.getZ() >> 4;
        Link link = linkedSensor(stack);
        BlockPos pos = link.pos();
        int distance = Math.abs(sourceX - (pos.getX() >> 4))
                + Math.abs(sourceZ - (pos.getZ() >> 4));
        DELIVERIES.computeIfAbsent(level,
                ignored -> new PriorityQueue<>(Comparator.comparingLong(Delivery::dueTick)))
                .add(new Delivery(level.getGameTime() + 1L + distance + extraDelay, pos,
                        frequency + 1, stack, sender));
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 100 == 0) {
            for (ServerPlayer player : level.players()) {
                for (ItemStack stack : player.getInventory()) {
                    if (isRemote(stack)) setModel(stack, linkStatus(stack, level) == LinkStatus.READY);
                }
            }
        }
        PriorityQueue<Delivery> queue = DELIVERIES.get(level);
        if (queue == null) return;
        while (!queue.isEmpty() && queue.peek().dueTick() <= level.getGameTime()) {
            Delivery delivery = queue.poll();
            BlockPos pos = delivery.pos();
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                deliveryUnavailable(delivery, level, LinkStatus.UNLOADED);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isSensor(state) || !(level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sensor)) {
                deliveryUnavailable(delivery, level, LinkStatus.MISSING);
                continue;
            }
            if (!SculkSensorBlock.canActivate(state)) continue;
            if (state.is(Blocks.CALIBRATED_SCULK_SENSOR)) {
                Direction back = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
                int selected = level.getSignal(pos.relative(back), back);
                if (selected != 0 && selected != delivery.frequency()) continue;
            }
            // Vanilla resonance supports only frequencies 1-15.
            int vibrationFrequency = Math.min(delivery.frequency(), 15);
            sensor.setLastVibrationFrequency(vibrationFrequency);
            ((SculkSensorBlock) state.getBlock()).activate(null, level, pos, state, 15, vibrationFrequency);
        }
        if (queue.isEmpty()) DELIVERIES.remove(level);
    }

    public static void onChestNeighborUpdate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Set<BlockPos> powered = POWERED_CHESTS.get(level);
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.TRAPPED_CHEST)) {
            if (powered != null) powered.remove(pos);
            return;
        }
        if (!level.hasNeighborSignal(pos)) {
            if (powered != null) powered.remove(pos);
            return;
        }
        if (powered == null) powered = POWERED_CHESTS.computeIfAbsent(level, ignored -> new HashSet<>());
        if (!powered.add(pos.immutable())) return;
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (isRemote(stack)) transmit(stack, level, pos, 1, null);
        }
    }

    public static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        Set<BlockPos> powered = POWERED_CHESTS.get(level);
        if (powered == null) return;
        powered.removeIf(pos -> (pos.getX() >> 4) == chunk.getPos().x()
                && (pos.getZ() >> 4) == chunk.getPos().z());
        if (powered.isEmpty()) POWERED_CHESTS.remove(level);
    }

    public static void clear() {
        DELIVERIES.clear();
        POWERED_CHESTS.clear();
    }
}
