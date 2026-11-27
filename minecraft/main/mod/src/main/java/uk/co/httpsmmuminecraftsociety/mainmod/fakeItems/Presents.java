package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Presents {
    private static final String PHASE = "mainmod_present_phase";
    private static final String START = "mainmod_present_start";
    private static final String LAST = "mainmod_present_last";
    private static final String DEADLINE = "mainmod_present_deadline";

    private Presents() {}

    public static boolean isPaper(ItemStack stack) {
        var item = FakeItems.getFakeItemFromStack(stack);
        return item != null && item.id().startsWith("wrapping-paper-");
    }

    public static boolean isPresent(ItemStack stack) {
        var item = FakeItems.getFakeItemFromStack(stack);
        return item != null && item.id().startsWith("present-");
    }

    public static ItemStack wrap(ItemStack paper, List<ItemStack> contents) {
        String color = FakeItems.getFakeItemFromStack(paper).id().substring("wrapping-paper-".length());
        ItemStack present = FakeItems.createFakeItemStack("present-" + color, 1);
        present.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return present;
    }

    public static InteractionResult interact(ItemFrame frame, Player player) {
        if (!frame.entityTags().contains(DecoBlocksManager.DECO_BLOCK_FRAME_TAG)
                || !isPresent(frame.getItem())) return null;
        if (!(frame.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack present = frame.getItem().copy();
        CompoundTag tag = data(present);
        if (tag.getIntOr(PHASE, -1) >= 0) return InteractionResult.SUCCESS_SERVER;
        long start = (level.getGameTime() / 8 + 1) * 8;
        tag.putInt(PHASE, 0);
        tag.putLong(START, start);
        save(frame, present, tag, 0);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static void tick(ItemFrame frame) {
        if (!(frame.level() instanceof ServerLevel level) || !isPresent(frame.getItem())) return;
        ItemStack present = frame.getItem().copy();
        CompoundTag tag = data(present);
        int phase = tag.getIntOr(PHASE, -1);
        long now = level.getGameTime();
        if (phase == 0 && now >= tag.getLongOr(START, Long.MAX_VALUE)) {
            tag.putInt(PHASE, 1);
            save(frame, present, tag, 1);
        } else if (phase == 1 && now >= tag.getLongOr(START, Long.MAX_VALUE) + 8) {
            tag.putInt(PHASE, 2);
            save(frame, present, tag, 2);
            sparkle(level, frame);
        } else if (phase == 2) {
            List<ItemStack> contents = present.getOrDefault(DataComponents.CONTAINER,
                    ItemContainerContents.EMPTY).itemCopies().toList();
            if (contents.isEmpty()) {
                finish(frame);
                return;
            }
            ItemStack next = contents.getFirst();
            List<ItemStack> remaining = new ArrayList<>(contents.subList(1, contents.size()));
            present.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(remaining));
            if (remaining.isEmpty()) {
                tag.putInt(PHASE, 3);
                tag.putLong(DEADLINE, now + 20);
            }
            save(frame, present, tag, 2);
            ItemEntity item = new ItemEntity(level, frame.getX(), frame.getY() + 0.55,
                    frame.getZ(), next);
            var random = level.getRandom();
            item.setDeltaMovement((random.nextDouble() - 0.5) * 0.16,
                    0.48 + random.nextDouble() * 0.12,
                    (random.nextDouble() - 0.5) * 0.16);
            level.addFreshEntity(item);
            if (remaining.isEmpty()) {
                tag.putString(LAST, item.getUUID().toString());
                save(frame, present, tag, 2);
            }
            sparkle(level, frame);
        } else if (phase == 3) {
            Entity last = null;
            try {
                last = level.getEntity(UUID.fromString(tag.getStringOr(LAST, "")));
            } catch (IllegalArgumentException ignored) {
                // The deadline also handles a missing or collected final item.
            }
            if (last != null && last.getDeltaMovement().y <= 0 || now >= tag.getLongOr(DEADLINE, now)) {
                finish(frame);
            }
        }
    }

    public static void resetForDrop(ItemStack present) {
        if (!isPresent(present)) return;
        CompoundTag tag = data(present);
        tag.remove(PHASE);
        tag.remove(START);
        tag.remove(LAST);
        tag.remove(DEADLINE);
        present.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        setModel(present, 0);
    }

    private static void finish(ItemFrame frame) {
        frame.setItem(ItemStack.EMPTY, false);
        frame.discard();
    }

    private static CompoundTag data(ItemStack present) {
        return present.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemFrame frame, ItemStack present, CompoundTag tag, int model) {
        present.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        setModel(present, model);
        frame.setItem(present, false);
    }

    private static void setModel(ItemStack present, int model) {
        CustomModelData data = present.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        present.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) model), data.flags(), data.strings(), data.colors()));
    }

    private static void sparkle(ServerLevel level, ItemFrame frame) {
        double x = frame.getX(), y = frame.getY() + 0.65, z = frame.getZ();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 4, 0.3, 0.2, 0.3, 0.05);
        level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 4, 0.3, 0.2, 0.3, 0.05);
        level.sendParticles(ParticleTypes.WAX_OFF, x, y, z, 4, 0.3, 0.2, 0.3, 0.05);
    }
}
