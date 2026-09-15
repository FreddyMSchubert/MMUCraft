package uk.co.httpsmmuminecraftsociety.mainmod.hopper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class HopperFilter {
    private static final String FILTER_KEY = "mainmod.hopper_filter";
    private static final String WHITELIST_KEY = "mainmod.hopper_filter_whitelist";
    private static final String BLACKLIST_KEY = "mainmod.hopper_filter_blacklist";
    private static final int MAX_SAVED_ENTRIES = 256;

    public enum Mode {
        WHITELIST_SINGLE("hopper-filter-whitelist-single", "Whitelist Single (+)", ChatFormatting.GREEN, false, false),
        WHITELIST_GROUP("hopper-filter-whitelist-group", "Whitelist Group (#)", ChatFormatting.GREEN, true, false),
        BLACKLIST_SINGLE("hopper-filter-blacklist-single", "Blacklist Single (+)", ChatFormatting.RED, false, true),
        BLACKLIST_GROUP("hopper-filter-blacklist-group", "Blacklist Group (#)", ChatFormatting.RED, true, true);

        private final String itemId;
        private final String label;
        private final ChatFormatting color;
        private final boolean group;
        private final boolean blacklist;

        Mode(String itemId, String label, ChatFormatting color, boolean group, boolean blacklist) {
            this.itemId = itemId;
            this.label = label;
            this.color = color;
            this.group = group;
            this.blacklist = blacklist;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private HopperFilter() {}

    public static ItemStack create() {
        ItemStack stack = FakeItems.createFakeItemStack(Mode.WHITELIST_SINGLE.itemId, 1);
        refreshTooltip(stack);
        return stack;
    }

    public static boolean isFilter(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getBooleanOr(FILTER_KEY, false) || mode(stack) != null;
    }

    public static boolean canConfigure(List<ItemStack> stacks) {
        ItemStack filter = findSingleFilter(stacks);
        if (filter.isEmpty()) return false;
        List<ItemStack> items = stacks.stream()
                .filter(stack -> !stack.isEmpty() && !isFilter(stack))
                .toList();
        Mode mode = mode(filter);
        return items.isEmpty() || !mode.group || !HopperFilterGroups.containingAll(items).isEmpty();
    }

    public static ItemStack configure(List<ItemStack> stacks) {
        ItemStack original = findSingleFilter(stacks);
        if (original.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = original.copyWithCount(1);
        Mode mode = mode(result);
        List<ItemStack> items = stacks.stream()
                .filter(stack -> !stack.isEmpty() && !isFilter(stack))
                .toList();

        if (items.isEmpty()) {
            setMode(result, mode.next());
        } else if (mode.group) {
            List<HopperFilterGroups.Group> candidates = HopperFilterGroups.containingAll(items);
            if (candidates.isEmpty()) return ItemStack.EMPTY;
            String group = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).id();
            toggle(result, mode.blacklist ? BLACKLIST_KEY : WHITELIST_KEY, "#" + group);
        } else {
            for (ItemStack item : items) toggle(result, mode.blacklist ? BLACKLIST_KEY : WHITELIST_KEY, itemKey(item));
        }

        refreshTooltip(result);
        return result;
    }

    public static boolean allows(Container hopper, ItemStack candidate) {
        ItemStack filter = ItemStack.EMPTY;
        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            if (isFilter(hopper.getItem(slot))) {
                filter = hopper.getItem(slot);
                break;
            }
        }
        if (filter.isEmpty()) return true;

        Set<String> blacklist = entries(filter, BLACKLIST_KEY);
        if (matches(blacklist, candidate)) return false;
        Set<String> whitelist = entries(filter, WHITELIST_KEY);
        return whitelist.isEmpty() || matches(whitelist, candidate);
    }

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.WATER_CAULDRON) || !isFilter(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && clean(stack)) {
            LayeredCauldronBlock.lowerFillLevel(level.getBlockState(pos), level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean clean(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean changed = data.remove(WHITELIST_KEY) != null | data.remove(BLACKLIST_KEY) != null;
        if (!changed) return false;
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        refreshTooltip(stack);
        return true;
    }

    private static boolean matches(Set<String> entries, ItemStack stack) {
        String key = itemKey(stack);
        return entries.stream().anyMatch(entry -> entry.startsWith("#")
                ? HopperFilterGroups.contains(entry.substring(1), stack)
                : entry.equals(key));
    }

    private static ItemStack findSingleFilter(List<ItemStack> stacks) {
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack stack : stacks) {
            if (!isFilter(stack)) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }

    static String baseItemId(ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        if (fakeItem != null) return MainMod.MOD_ID + ":" + fakeItem.id();
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static Optional<String> potionId(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
            return Optional.empty();
        }
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion()
                .flatMap(Holder::unwrapKey)
                .map(key -> key.identifier().toString());
    }

    static Set<String> storedEnchantmentIds(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return Set.of();
        return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                .map(Holder::unwrapKey)
                .flatMap(Optional::stream)
                .map(key -> key.identifier().toString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String itemKey(ItemStack stack) {
        String base = baseItemId(stack);
        Optional<String> potion = potionId(stack);
        if (potion.isPresent()) return base + "@" + potion.get();
        Set<String> enchantments = storedEnchantmentIds(stack);
        return enchantments.isEmpty() ? base : base + "@" + String.join(",", enchantments.stream().sorted().toList());
    }

    private static Mode mode(ItemStack stack) {
        for (Mode mode : Mode.values()) {
            if (FakeItems.isSpecificFakeItem(stack, mode.itemId)) return mode;
        }
        return null;
    }

    private static void setMode(ItemStack stack, Mode mode) {
        CustomModelData current = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        List<String> strings = new ArrayList<>(current.strings());
        if (strings.isEmpty()) strings.add(mode.itemId);
        else strings.set(0, mode.itemId);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(current.floats(), current.flags(), List.copyOf(strings), current.colors()));
    }

    private static void toggle(ItemStack stack, String key, String value) {
        Set<String> values = entries(stack, key);
        if (!values.remove(value) && values.size() < MAX_SAVED_ENTRIES) values.add(value);
        writeEntries(stack, key, values);
    }

    private static Set<String> entries(ItemStack stack, String key) {
        ListTag list = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getListOrEmpty(key);
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < Math.min(list.size(), MAX_SAVED_ENTRIES); index++) {
            list.getString(index).filter(value -> value.length() <= 256).ifPresent(values::add);
        }
        return values;
    }

    private static void writeEntries(ItemStack stack, String key, Set<String> values) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (values.isEmpty()) {
            data.remove(key);
        } else {
            ListTag list = new ListTag();
            values.stream().sorted().map(StringTag::valueOf).forEach(list::add);
            data.put(key, list);
        }
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private static void refreshTooltip(ItemStack stack) {
        Mode mode = mode(stack);
        if (mode == null) return;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putBoolean(FILTER_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(mode.label).withStyle(mode.color));
        addList(lines, "Whitelist", entries(stack, WHITELIST_KEY), ChatFormatting.GREEN);
        addList(lines, "Blacklist", entries(stack, BLACKLIST_KEY), ChatFormatting.RED);
        stack.set(DataComponents.LORE, new ItemLore(lines.stream()
                .map(line -> line.copy().withStyle(style -> style.withItalic(false)))
                .map(Component.class::cast)
                .toList()));
    }

    private static void addList(List<Component> lines, String heading, Set<String> entries, ChatFormatting color) {
        if (entries.isEmpty()) return;
        lines.add(Component.literal(heading + ":").withStyle(color));
        entries.stream()
                .sorted(Comparator.comparing((String entry) -> !entry.startsWith("#"))
                        .thenComparing(entry -> displayName(entry).getString().toLowerCase(Locale.ROOT)))
                .map(HopperFilter::displayName)
                .map(name -> Component.literal("  ").append(name).withStyle(color))
                .forEach(lines::add);
    }

    private static Component displayName(String entry) {
        if (entry.startsWith("#")) {
            return Component.literal("#" + HopperFilterGroups.name(entry.substring(1)));
        }
        int componentSeparator = entry.indexOf('@');
        String base = componentSeparator < 0 ? entry : entry.substring(0, componentSeparator);
        String componentIds = componentSeparator < 0 ? "" : entry.substring(componentSeparator + 1);
        Identifier id = Identifier.tryParse(base);
        if (id == null) return Component.literal(entry);
        if (id.getNamespace().equals(MainMod.MOD_ID)) {
            FakeItem item = FakeItems.ID_MAP.get(id.getPath());
            return Component.literal(item == null ? entry : item.title());
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return Component.literal(entry);
        if (!componentIds.isEmpty() && (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION)) {
            Identifier potionId = Identifier.tryParse(componentIds);
            if (potionId != null) return Component.translatable(item.getDescriptionId() + ".effect." + potionId.getPath());
        }
        if (!componentIds.isEmpty() && item == Items.ENCHANTED_BOOK) {
            var name = Component.translatable(item.getDescriptionId()).append(" — ");
            boolean first = true;
            for (String raw : componentIds.split(",")) {
                Identifier enchantmentId = Identifier.tryParse(raw);
                if (enchantmentId == null) continue;
                if (!first) name.append(", ");
                name.append(Component.translatable("enchantment." + enchantmentId.getNamespace() + "." + enchantmentId.getPath()));
                first = false;
            }
            if (!first) return name;
        }
        return item.getName(new ItemStack(item));
    }
}
