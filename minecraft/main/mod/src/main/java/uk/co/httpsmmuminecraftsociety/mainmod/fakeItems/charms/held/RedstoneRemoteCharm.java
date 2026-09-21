package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
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
import net.minecraft.world.phys.HitResult;
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
import java.util.WeakHashMap;

public final class RedstoneRemoteCharm implements Charm, UseCallbackCharm, UseOnBlockCallbackCharm,
        AttackBlockCallbackCharm, AttackEntityCallbackCharm, AttackSwingCallbackCharm, BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 57;
    private static final String FREQUENCY_KEY = "mainmod_remote_frequency";
    private static final String SENSORS_KEY = "mainmod_remote_sensors";
    private static final String LABELS_KEY = "mainmod_remote_labels";
    private static final String DIMENSION_KEY = "dimension";
    private static final String POSITION_KEY = "position";
    private static final Map<ServerLevel, PriorityQueue<Delivery>> DELIVERIES = new HashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> POWERED_CHESTS = new HashMap<>();
    private static final Map<ServerPlayer, PendingAttackSwing> ATTACK_SWINGS = new WeakHashMap<>();
    private static final Map<ServerPlayer, PendingBlockUse> BLOCK_USES = new WeakHashMap<>();
    private static final Map<ServerPlayer, PendingRightUse> RIGHT_USES = new WeakHashMap<>();

    private record Link(String dimension, BlockPos pos) {}
    private record Delivery(long dueTick, BlockPos pos, int frequency, ItemStack remote,
                            @Nullable ServerPlayer sender) {}
    private record PendingAttackSwing(long tick, InteractionHand hand) {}
    private record PendingBlockUse(long tick, ItemStack stack) {}
    private record PendingRightUse(long tick, int count) {}
    private enum LinkStatus { READY, UNLINKED, OTHER_DIMENSION, UNLOADED, MISSING, BUSY, MISMATCH }

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
        // A server-only item is reported as PASS by the client prediction. For a block
        // target the client then sends a second UseItem packet as a fallback. The block
        // callback already handled that click, so consume exactly that paired packet.
        PendingBlockUse blockUse = BLOCK_USES.get(player);
        if (blockUse != null) {
            long age = level.getGameTime() - blockUse.tick();
            BLOCK_USES.remove(player);
            if (age >= 0 && age <= 2 && blockUse.stack() == stack) return InteractionResult.SUCCESS_SERVER;
        }
        if (player.isShiftKeyDown()) {
            if (player.getMainHandItem() == stack
                    && player.pick(player.blockInteractionRange(), 1.0F, false).getType() == HitResult.Type.MISS) {
                PendingRightUse pending = RIGHT_USES.get(player);
                long tick = level.getGameTime();
                RIGHT_USES.put(player, pending != null && pending.tick() == tick
                        ? new PendingRightUse(tick, pending.count() + 1)
                        : new PendingRightUse(tick, 1));
            }
            tune(stack, player, level, 1);
        }
        else transmit(stack, level, player.blockPosition(), 0, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onUseOnBlock(ItemStack stack, ServerPlayer player, ServerLevel level,
                                          InteractionHand hand, BlockHitResult hit, int charmLevel) {
        if (stack != player.getItemInHand(hand)) return InteractionResult.PASS;
        pruneDestroyedLinks(stack, level);
        if (player.isShiftKeyDown()) tune(stack, player, level, 1);
        else if (isSensor(level.getBlockState(hit.getBlockPos()))) link(stack, player, level, hit.getBlockPos());
        else transmit(stack, level, player.blockPosition(), 0, player);
        BLOCK_USES.put(player, new PendingBlockUse(level.getGameTime(), stack));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult onAttackBlock(ItemStack stack, ServerPlayer player, ServerLevel level,
                                           InteractionHand hand, BlockPos pos, Direction direction, int charmLevel) {
        if (stack != player.getItemInHand(hand) || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        tune(stack, player, level, -1);
        ATTACK_SWINGS.put(player, new PendingAttackSwing(level.getGameTime(), hand));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void onAttackSwing(ItemStack stack, ServerPlayer player, ServerLevel level,
                              InteractionHand hand, int charmLevel) {
        if (hand == InteractionHand.MAIN_HAND) {
            PendingRightUse rightUse = RIGHT_USES.get(player);
            if (rightUse != null) {
                long age = level.getGameTime() - rightUse.tick();
                if (age >= 0 && age <= 1) {
                    if (rightUse.count() == 1) RIGHT_USES.remove(player);
                    else RIGHT_USES.put(player, new PendingRightUse(rightUse.tick(), rightUse.count() - 1));
                    return;
                }
                RIGHT_USES.remove(player);
            }
        }
        PendingAttackSwing attack = ATTACK_SWINGS.remove(player);
        if (attack != null && attack.hand() == hand && level.getGameTime() - attack.tick() <= 2) return;
        // A successful vanilla block interaction can also send a swing packet on right-click.
        if (player.pick(player.blockInteractionRange(), 1.0F, false).getType() != HitResult.Type.MISS) return;
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) tune(stack, player, level, -1);
    }

    @Override
    public InteractionResult onAttackEntity(ItemStack stack, ServerPlayer player, ServerLevel level,
                                            InteractionHand hand, Entity entity, @Nullable EntityHitResult hit,
                                            int charmLevel) {
        if (stack != player.getItemInHand(hand) || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        tune(stack, player, level, -1);
        ATTACK_SWINGS.put(player, new PendingAttackSwing(level.getGameTime(), hand));
        return InteractionResult.SUCCESS_SERVER;
    }

    public static boolean isRemote(ItemStack stack) {
        return CharmStackData.getSingleStoredCharm(stack)
                .filter(charm -> charm.charmId() == CHARM_ID && charm.level() > 0)
                .isPresent();
    }

    public static void clearLink(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.remove(SENSORS_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
        setModel(stack, false);
    }

    private static boolean isSensor(BlockState state) {
        return state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SENSOR);
    }

    private static int frequency(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(FREQUENCY_KEY, 15), 16);
    }

    private static int selectedFrequency(ItemStack stack) {
        return (frequency(stack) + 1) % 16;
    }

    private static String frequencyLabel(int selectedFrequency) {
        return selectedFrequency == 0 ? "/" : Integer.toString(selectedFrequency);
    }

    public static void labelSelectedFrequency(ItemStack stack, String label) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag labels = data.getCompound(LABELS_KEY).orElseGet(CompoundTag::new);
        labels.putString(Integer.toString(selectedFrequency(stack)), label);
        data.put(LABELS_KEY, labels);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
    }

    private static Map<Integer, String> frequencyLabels(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entries = data.getCompound(LABELS_KEY).orElse(null);
        if (entries == null) return Map.of();
        Map<Integer, String> labels = new HashMap<>();
        for (int frequency = 0; frequency <= 15; frequency++) {
            String label = entries.getStringOr(Integer.toString(frequency), "");
            if (!label.isBlank()) labels.put(frequency, label);
        }
        return labels;
    }

    private static void tune(ItemStack stack, ServerPlayer player, ServerLevel level, int step) {
        int next = Math.floorMod(frequency(stack) + step, 16);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(FREQUENCY_KEY, next);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
        refreshRemote(stack, level);
        int selected = (next + 1) % 16;
        String label = frequencyLabels(stack).get(selected);
        player.sendSystemMessage(Component.literal("Remote frequency: " + frequencyLabel(selected)
                + (label == null ? "" : ": " + label)), true);
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static Map<Integer, Link> linkedSensors(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entries = data.getCompound(SENSORS_KEY).orElse(null);
        if (entries == null) return Map.of();

        Map<Integer, Link> links = new HashMap<>();
        for (int selectedFrequency = 0; selectedFrequency <= 15; selectedFrequency++) {
            CompoundTag entry = entries.getCompound(Integer.toString(selectedFrequency)).orElse(null);
            if (entry == null) continue;
            String dimension = entry.getStringOr(DIMENSION_KEY, "");
            if (dimension.isEmpty() || entry.getLong(POSITION_KEY).isEmpty()) continue;
            links.put(selectedFrequency, new Link(dimension,
                    BlockPos.of(entry.getLongOr(POSITION_KEY, 0))));
        }
        return links;
    }

    private static @Nullable Link linkedSensor(ItemStack stack, int selectedFrequency) {
        return linkedSensors(stack).get(selectedFrequency);
    }

    private static void writeLink(ItemStack stack, int selectedFrequency, Link link) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entries = data.getCompound(SENSORS_KEY).orElseGet(CompoundTag::new);
        CompoundTag entry = new CompoundTag();
        entry.putString(DIMENSION_KEY, link.dimension());
        entry.putLong(POSITION_KEY, link.pos().asLong());
        entries.put(Integer.toString(selectedFrequency), entry);
        data.put(SENSORS_KEY, entries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
    }

    private static boolean removeLink(ItemStack stack, int selectedFrequency, @Nullable BlockPos expectedPos) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag entries = data.getCompound(SENSORS_KEY).orElse(null);
        if (entries == null) return false;
        CompoundTag entry = entries.getCompound(Integer.toString(selectedFrequency)).orElse(null);
        if (entry == null) return false;
        if (expectedPos != null && entry.getLongOr(POSITION_KEY, Long.MIN_VALUE) != expectedPos.asLong()) return false;
        entries.remove(Integer.toString(selectedFrequency));
        if (entries.isEmpty()) data.remove(SENSORS_KEY);
        else data.put(SENSORS_KEY, entries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        updateLore(stack);
        return true;
    }

    private static boolean modelLit(ItemStack stack) {
        List<Boolean> flags = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).flags();
        return !flags.isEmpty() && flags.getFirst();
    }

    private static boolean setModel(ItemStack stack, boolean lit) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        float selectedFrequency = selectedFrequency(stack);
        if (!data.floats().isEmpty() && data.floats().getFirst() == selectedFrequency
                && !data.flags().isEmpty() && data.flags().getFirst() == lit) return false;
        List<Float> floats = new ArrayList<>(data.floats());
        if (floats.isEmpty()) floats.add(selectedFrequency);
        else floats.set(0, selectedFrequency);
        List<Boolean> flags = new ArrayList<>(data.flags());
        if (flags.isEmpty()) flags.add(lit);
        else flags.set(0, lit);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.copyOf(floats), List.copyOf(flags), data.strings(), data.colors()));
        return true;
    }

    private static void updateLore(ItemStack stack) {
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(CHARM_ID);
        List<Component> lore = new ArrayList<>();
        if (fakeItem != null) {
            CharmItemFeature feature = fakeItem.getFeature(CharmItemFeature.class);
            if (feature != null) lore.addAll(feature.buildTooltip(1));
        }
        if (lore.isEmpty()) lore.addAll(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        int selectedFrequency = selectedFrequency(stack);
        Map<Integer, Link> links = linkedSensors(stack);
        Map<Integer, String> labels = frequencyLabels(stack);
        boolean padFrequencies = selectedFrequency >= 10
                || links.keySet().stream().anyMatch(frequency -> frequency >= 10)
                || labels.keySet().stream().anyMatch(frequency -> frequency >= 10);
        lore.add(Component.literal("Frequencies:").withStyle(ChatFormatting.WHITE));
        for (int frequency = 0; frequency <= 15; frequency++) {
            Link linked = links.get(frequency);
            String label = labels.get(frequency);
            boolean selected = frequency == selectedFrequency;
            if (linked == null && label == null && !selected) continue;
            String separator = padFrequencies && frequency < 10 ? "  - " : " - ";
            MutableComponent line = Component.literal(selected ? "> " : "  ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(frequencyLabel(frequency) + separator)
                            .withStyle(style -> style.withColor(TextColor.fromRgb(selected ? 0xFF4444 : 0xFF9999))));
            line.append(Component.literal(linked == null ? "[Not paired]" : formatPosition(linked))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(selected ? 0x55AAFF : 0xA9D8FF))));
            if (label != null) {
                line.append(Component.literal(" - " + label)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(selected ? 0xFFFFFF : 0xBBBBBB))));
            }
            lore.add(line);
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        FakeItems.wrapTooltip(stack);
    }

    private static String formatPosition(Link link) {
        BlockPos pos = link.pos();
        String dimension = switch (link.dimension()) {
            case "minecraft:overworld" -> "";
            case "minecraft:the_nether" -> "|Nether";
            case "minecraft:the_end" -> "|End";
            default -> "|" + link.dimension();
        };
        return "[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + dimension + "]";
    }

    private static int sensorFrequency(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.CALIBRATED_SCULK_SENSOR)) return 0;
        Direction back = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
        return level.getSignal(pos.relative(back), back);
    }

    private static void link(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos pos) {
        int selectedFrequency = selectedFrequency(stack);
        BlockState state = level.getBlockState(pos);
        if (sensorFrequency(level, pos, state) != selectedFrequency) {
            player.sendSystemMessage(Component.literal("Failed to pair, frequencies don't match."), true);
            return;
        }

        Link current = linkedSensor(stack, selectedFrequency);
        Link target = new Link(dimension(player.level()), pos.immutable());
        writeLink(stack, selectedFrequency, target);
        refreshRemote(stack, level);
        if (target.equals(current)) {
            player.sendSystemMessage(Component.literal("Sensor already linked."), true);
            return;
        }
        player.sendSystemMessage(Component.literal(current == null
                ? "Sensor linked."
                : "Sensor link replaced."), true);
    }

    private static LinkStatus linkStatus(ItemStack stack, ServerLevel level, int selectedFrequency) {
        Link link = linkedSensor(stack, selectedFrequency);
        if (link == null) return LinkStatus.UNLINKED;
        if (!link.dimension().equals(dimension(level))) return LinkStatus.OTHER_DIMENSION;
        BlockPos pos = link.pos();
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return LinkStatus.UNLOADED;
        BlockState state = level.getBlockState(pos);
        if (!isSensor(state)
                || !(level.getBlockEntity(pos) instanceof SculkSensorBlockEntity)) return LinkStatus.MISSING;
        if (!SculkSensorBlock.canActivate(state)) return LinkStatus.BUSY;
        if (state.is(Blocks.CALIBRATED_SCULK_SENSOR)) {
            int selected = sensorFrequency(level, pos, state);
            if (selected != 0 && selected != selectedFrequency) return LinkStatus.MISMATCH;
        }
        return LinkStatus.READY;
    }

    private static boolean pruneDestroyedLinks(ItemStack stack, ServerLevel level) {
        Map<Integer, Link> links = linkedSensors(stack);
        List<Integer> removed = new ArrayList<>();
        for (Map.Entry<Integer, Link> entry : links.entrySet()) {
            Link link = entry.getValue();
            if (!link.dimension().equals(dimension(level))) continue;
            BlockPos pos = link.pos();
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isSensor(state) || !(level.getBlockEntity(pos) instanceof SculkSensorBlockEntity)) {
                removed.add(entry.getKey());
            }
        }
        for (int selectedFrequency : removed) removeLink(stack, selectedFrequency, null);
        return !removed.isEmpty();
    }

    private static boolean refreshRemote(ItemStack stack, ServerLevel level) {
        boolean linksChanged = pruneDestroyedLinks(stack, level);
        boolean modelChanged = setModel(stack, linkStatus(stack, level, selectedFrequency(stack)) == LinkStatus.READY);
        return linksChanged || modelChanged;
    }

    private static void reportUnavailable(@Nullable ServerPlayer player, ItemStack stack,
                                          int selectedFrequency, LinkStatus status) {
        if (player == null) return;
        String frequency = frequencyLabel(selectedFrequency);
        String label = frequencyLabels(stack).get(selectedFrequency);
        String message = switch (status) {
            case UNLINKED -> "No sensor linked for frequency " + frequency
                    + (label == null ? "" : " (" + label + ")") + ".";
            case OTHER_DIMENSION, UNLOADED, MISSING -> "Unable to reach sensor"
                    + (label == null ? "" : " (" + label + ")") + " on frequency " + frequency + ".";
            case BUSY -> "Sensor" + (label == null ? "" : " (" + label + ")")
                    + " is busy on frequency " + frequency + ".";
            case MISMATCH -> "Sensor" + (label == null ? "" : " (" + label + ")")
                    + " no longer matches frequency " + frequency + ".";
            case READY -> null;
        };
        if (message != null) player.sendSystemMessage(Component.literal(message), true);
    }

    private static void deliveryUnavailable(Delivery delivery, ServerLevel level, LinkStatus status) {
        Link current = linkedSensor(delivery.remote(), delivery.frequency());
        boolean sameLink = current != null && current.dimension().equals(dimension(level))
                && current.pos().equals(delivery.pos());
        boolean changed = sameLink && status == LinkStatus.MISSING
                && removeLink(delivery.remote(), delivery.frequency(), delivery.pos());
        if ((sameLink || current == null) && selectedFrequency(delivery.remote()) == delivery.frequency()) {
            changed |= setModel(delivery.remote(), false);
        }
        if (changed && delivery.sender() != null) syncInventory(delivery.sender());
        reportUnavailable(delivery.sender(), delivery.remote(), delivery.frequency(), status);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static void transmit(ItemStack stack, ServerLevel level, BlockPos source, int extraDelay,
                                 @Nullable ServerPlayer sender) {
        int selectedFrequency = selectedFrequency(stack);
        if (refreshRemote(stack, level) && sender != null) syncInventory(sender);
        LinkStatus status = linkStatus(stack, level, selectedFrequency);
        if (status != LinkStatus.READY) {
            reportUnavailable(sender, stack, selectedFrequency, status);
            return;
        }
        int frequency = Math.max(selectedFrequency - 1, 0);
        float pitch = NoteBlock.getPitchFromNote(frequency);
        level.playSound(null, source, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, pitch);
        int sourceX = source.getX() >> 4;
        int sourceZ = source.getZ() >> 4;
        Link link = linkedSensor(stack, selectedFrequency);
        BlockPos pos = link.pos();
        int distance = Math.abs(sourceX - (pos.getX() >> 4))
                + Math.abs(sourceZ - (pos.getZ() >> 4));
        DELIVERIES.computeIfAbsent(level,
                ignored -> new PriorityQueue<>(Comparator.comparingLong(Delivery::dueTick)))
                .add(new Delivery(level.getGameTime() + 1L + distance + extraDelay, pos,
                        selectedFrequency, stack, sender));
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                boolean changed = false;
                for (ItemStack stack : player.getInventory()) {
                    if (isRemote(stack)) changed |= refreshRemote(stack, level);
                }
                if (changed) syncInventory(player);
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
            if (!SculkSensorBlock.canActivate(state)) {
                deliveryUnavailable(delivery, level, LinkStatus.BUSY);
                continue;
            }
            if (state.is(Blocks.CALIBRATED_SCULK_SENSOR)) {
                Direction back = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
                int selected = level.getSignal(pos.relative(back), back);
                if (selected != 0 && selected != delivery.frequency()) {
                    deliveryUnavailable(delivery, level, LinkStatus.MISMATCH);
                    continue;
                }
            }
            // An uncalibrated sensor uses 0; vanilla resonance uses 1-15.
            int vibrationFrequency = Math.min(delivery.frequency(), 15);
            sensor.setLastVibrationFrequency(vibrationFrequency);
            ((SculkSensorBlock) state.getBlock()).activate(null, level, pos, state, 15, vibrationFrequency);
            if (selectedFrequency(delivery.remote()) == delivery.frequency()) {
                Link current = linkedSensor(delivery.remote(), delivery.frequency());
                if (current != null && current.dimension().equals(dimension(level))
                        && current.pos().equals(pos) && setModel(delivery.remote(), false)
                        && delivery.sender() != null) syncInventory(delivery.sender());
            }
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
        ATTACK_SWINGS.clear();
        BLOCK_USES.clear();
        RIGHT_USES.clear();
    }
}
