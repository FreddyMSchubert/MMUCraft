package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.RecordFishCatchResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.UnlockBookLoot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class FishingCatches {
    private static final String LENGTH_TAG = "mainmod_fish_length_cm";
    private static final String RARITY_TAG = "mainmod_fish_rarity";
    private static final Map<FishRarity, List<FishLoot>> FISH_LOOT = emptyLootTable();
    private static final Map<FishRarity, List<ItemStack>> TREASURE_LOOT = emptyLootTable();

    static {
        addFish(FishRarity.COMMON, new ItemStack(Items.COD), 53.0, 25.0);
        addFish(FishRarity.COMMON, new ItemStack(Items.SALMON), 75.0, 35.0);
        addFish(FishRarity.COMMON, new ItemStack(Items.TROPICAL_FISH), 15.0, 7.0);
        addFish(FishRarity.COMMON, new ItemStack(Items.PUFFERFISH), 30.0, 12.0);

        addTreasure(FishRarity.COMMON, new ItemStack(Items.SUGAR_CANE));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.ROTTEN_FLESH));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.STICK));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.STRING));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.BONE));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.TRIPWIRE_HOOK));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.LILY_PAD));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.KELP));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.SEAGRASS));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.SEA_PICKLE));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.FEATHER));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.FLINT));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.CHARCOAL));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.DEAD_BUSH));
        ItemStack damagedShovel = new ItemStack(Items.WOODEN_SHOVEL);
        damagedShovel.setDamageValue(damagedShovel.getMaxDamage() / 2);
        addTreasure(FishRarity.COMMON, damagedShovel);
        addTreasure(FishRarity.COMMON, new ItemStack(Items.OAK_BOAT));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.POISONOUS_POTATO));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.WHEAT));
        addTreasure(FishRarity.COMMON, new ItemStack(Items.COPPER_NUGGET));
        addTreasure(FishRarity.COMMON, FakeItems.createFakeItemStack("boot", 1));
        addTreasure(FishRarity.COMMON, FakeItems.createFakeItemStack("empty-can", 1));
        addTreasure(FishRarity.COMMON, FakeItems.createFakeItemStack("fish-bones", 1));
        addTreasure(FishRarity.COMMON, FakeItems.createFakeItemStack("old-tire", 1));
        addTreasure(FishRarity.COMMON, FakeItems.createFakeItemStack("seaweed", 1));

        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.BOWL));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.LEATHER_BOOTS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.INK_SAC));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.GLOW_INK_SAC));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.COAL).copyWithCount((int)Math.floor(Math.random() * 3)));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.LEATHER));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.CLAY_BALL).copyWithCount((int)Math.floor(Math.random() * 5)));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.CLAY));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.GLASS_BOTTLE));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.BOW));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.SADDLE));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.FISHING_ROD));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.GOLDEN_SWORD));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.IRON_NUGGET));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.RAW_COPPER));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.COCOA_BEANS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.MELON_SEEDS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.PUMPKIN_SEEDS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.MAP));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.CARROT));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.POTATO));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.WHEAT_SEEDS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.BEETROOT_SEEDS));
        addTreasure(FishRarity.UNCOMMON, new ItemStack(Items.EMERALD));

        addTreasure(FishRarity.RARE, new ItemStack(Items.RAW_IRON));
        addTreasure(FishRarity.RARE, new ItemStack(Items.RAW_GOLD));
        addTreasure(FishRarity.RARE, new ItemStack(Items.NAME_TAG));
        addTreasure(FishRarity.RARE, new ItemStack(Items.WET_SPONGE));
        addTreasure(FishRarity.RARE, new ItemStack(Items.SPONGE));
        addTreasure(FishRarity.RARE, new ItemStack(Items.TURTLE_SCUTE));
        addTreasure(FishRarity.RARE, new ItemStack(Items.COMPASS));
        addTreasure(FishRarity.RARE, new ItemStack(Items.CLOCK));
        addTreasure(FishRarity.RARE, new ItemStack(Items.SPYGLASS));
        addTreasure(FishRarity.RARE, new ItemStack(Items.EXPERIENCE_BOTTLE));
        addTreasure(FishRarity.RARE, FakeItems.createFakeItemStack("trident-shaft", 1));
        addTreasure(FishRarity.RARE, FakeItems.createFakeItemStack("sushi", 1));

        addTreasure(FishRarity.EPIC, new ItemStack(Items.EXPERIENCE_BOTTLE).copyWithCount((int)Math.floor(Math.random() * 10)));
        addTreasure(FishRarity.EPIC, PotionContents.createItemStack(Items.POTION, Potions.LUCK));
        addTreasure(FishRarity.EPIC, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.LUCK));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.NAUTILUS_SHELL));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.ANGLER_POTTERY_SHERD));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.AMETHYST_SHARD));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.PRISMARINE_SHARD));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.PRISMARINE_CRYSTALS));
        addTreasure(FishRarity.EPIC, new ItemStack(Items.EMERALD_BLOCK));

        addTreasure(FishRarity.LEGENDARY, new ItemStack(Items.HEART_OF_THE_SEA));
        addTreasure(FishRarity.LEGENDARY, FakeItems.createFakeItemStack("golden-nutritional-paste", 1));
        addTreasure(FishRarity.LEGENDARY, new ItemStack(Items.DIAMOND));
        addTreasure(FishRarity.LEGENDARY, new ItemStack(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE));
        addTreasure(FishRarity.LEGENDARY, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE));

        addTreasure(FishRarity.MYTHICAL, new ItemStack(Items.CONDUIT));
        addTreasure(FishRarity.MYTHICAL, new ItemStack(Items.TRIDENT));
        addTreasure(FishRarity.MYTHICAL, new ItemStack(Items.DIAMOND_BLOCK));
        addTreasure(FishRarity.MYTHICAL, new ItemStack(Items.SNIFFER_EGG));
    }

    private FishingCatches() {
    }

    public static Pair<ItemStack, FishingPersonality> random(FishingHook hook, double itemChance, int fishingLuckBonus) {
        double luck = hook.getPlayerOwner() == null
                ? 0.0
                : hook.getPlayerOwner().getAttributeValue(Attributes.LUCK) + fishingLuckBonus;
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            ItemStack unlockBook = UnlockBookLoot.rollFishingBook(player, hook.getRandom(), luck);
            if (!unlockBook.isEmpty()) {
                return Pair.of(unlockBook, defaultPersonality(FishRarity.COMMON, true));
            }
        }

        boolean treasure = hook.getRandom().nextDouble() < itemChance;
        FishRarity rarity = randomRarity(hook.getRandom(), luck);
        List<CatchEntry> entries = entriesFor(treasure, rarity, hook);
        CatchEntry selected = entries.get(hook.getRandom().nextInt(entries.size()));
        ItemStack stack = selected.stack().copy();
        decorateFish(stack, hook.getPlayerOwner(), selected.length(), hook.getRandom());
        if (selected.length() != null) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putInt(RARITY_TAG, selected.personality().rarity().ordinal()));
        }
        return Pair.of(stack, selected.personality());
    }

    public static void addFish(FishRarity rarity, ItemStack stack, double averageLengthCm, double deviationCm) {
        FISH_LOOT.get(rarity).add(new FishLoot(stack.copy(), new FishSize(averageLengthCm, deviationCm)));
    }

    public static void addTreasure(FishRarity rarity, ItemStack stack) {
        TREASURE_LOOT.get(rarity).add(stack.copy());
    }

    public static Optional<Component> catchMessage(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(LENGTH_TAG)) {
            return Optional.empty();
        }

        int rarityIndex = Math.max(0, Math.min(FishRarity.values().length - 1, tag.getIntOr(RARITY_TAG, 0)));
        FishRarity rarity = FishRarity.values()[rarityIndex];

        return Optional.of(Component.empty()
                .append(Component.literal(stack.getHoverName().getString() + " [" + rarity.displayName() + "]")
                        .withStyle(Style.EMPTY.withColor(rarity.colorRgb())))
                .append(Component.literal(" • " + formatLength(tag.getDoubleOr(LENGTH_TAG, 0.0)))
                        .withStyle(ChatFormatting.WHITE)));
    }

    public static void trackCatch(ServerPlayer player, ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(LENGTH_TAG)) {
            return; // Treasure has no length and is intentionally outside the compendium.
        }

        double lengthCm = tag.getDoubleOr(LENGTH_TAG, 0.0);
        int rarityIndex = Math.max(0, Math.min(FishRarity.values().length - 1, tag.getIntOr(RARITY_TAG, 0)));
        FishRarity rarity = FishRarity.values()[rarityIndex];
        String fishId = fakeItem == null
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                : fakeItem.id();
        GameplayGrpcService.recordFishCatch(
                player,
                fishId,
                lengthCm,
                rarity.name().toLowerCase(Locale.ROOT)
        ).thenAccept(response -> showRecordMessages(player, stack.getHoverName().getString(), rarity, lengthCm, response))
                .exceptionally(error -> {
                    MainMod.LOGGER.warn("Could not record fish catch for {}", player.getName().getString(), error);
                    return null;
                });
    }

    public static ItemStack claimDrop(ServerPlayer player, ItemStack stack, RandomSource random) {
        if (UnlockBookLoot.claimFishingDrop(player, stack)) return stack;
        List<ItemStack> commonTreasure = TREASURE_LOOT.get(FishRarity.COMMON);
        return commonTreasure.get(random.nextInt(commonTreasure.size())).copy();
    }

    private static void showRecordMessages(
            ServerPlayer player,
            String fishName,
            FishRarity rarity,
            double lengthCm,
            RecordFishCatchResponse response
    ) {
		if (!response.getRecorded()) return;
		if (response.getAnnounce()) {
			MinecraftServer server = player.level().getServer();
			server.execute(() -> {
				DiscordBridge.fishAnnouncement(server, player,
						"caught " + fishName + "!", response.getFirstServerCatchAnnouncement());
			});
		}
        List<Component> messages = new ArrayList<>();
        if (response.getFirstCatch()) {
            messages.add(recordMessage("First Catch", fishName, rarity, lengthCm));
        } else {
            if (response.getPersonalSizeRecord()) {
                messages.add(recordMessage("Personal Size Record", fishName, rarity, lengthCm));
            } else if (response.getPersonalSmallestRecord()) {
                messages.add(recordMessage("Personal Smallest Record", fishName, rarity, lengthCm));
            }
            if (response.getServerSizeRecord()) {
                messages.add(recordMessage("Server Size Record", fishName, rarity, lengthCm));
            } else if (response.getServerSmallestRecord()) {
                messages.add(recordMessage("Server Smallest Record", fishName, rarity, lengthCm));
            }
        }

        MinecraftServer server = player.level().getServer();
        for (int index = 0; index < messages.size(); index++) {
            Component message = messages.get(index);
            CompletableFuture.delayedExecutor(3_200L + index * 2_000L, TimeUnit.MILLISECONDS).execute(() ->
                    server.execute(() -> {
                        if (!player.hasDisconnected()) player.sendOverlayMessage(message);
                    })
            );
        }
    }

    private static Component recordMessage(String label, String fishName, FishRarity rarity, double lengthCm) {
        return Component.literal("★ " + label + " — " + fishName + " • " + formatLength(lengthCm))
                .withStyle(Style.EMPTY.withColor(rarity.colorRgb()).withBold(true));
    }

    private static FishRarity randomRarity(RandomSource random, double luck) {
        double total = 0.0;
        for (FishRarity rarity : FishRarity.values()) {
            total += rarity.weightAtLuck(luck);
        }

        double roll = random.nextDouble() * total;
        for (FishRarity rarity : FishRarity.values()) {
            // These are weights rather than percentages because interpolated totals need not equal 100.
            roll -= rarity.weightAtLuck(luck);
            if (roll <= 0.0) {
                return rarity;
            }
        }
        return FishRarity.MYTHICAL;
    }

    private static List<CatchEntry> entriesFor(boolean treasure, FishRarity rolledRarity, FishingHook hook) {
        for (int index = rolledRarity.ordinal(); index >= 0; index--) {
            FishRarity rarity = FishRarity.values()[index];
            List<CatchEntry> entries = entriesAt(treasure, rarity, hook);
            if (!entries.isEmpty()) {
                // Empty rarities fall through one step at a time, preserving the rolled group and nearest rarity.
                return entries;
            }
        }

        ItemStack fallback = new ItemStack(treasure ? Items.STICK : Items.SALMON);
        FishSize length = treasure ? null : new FishSize(75.0, 35.0);
        return List.of(new CatchEntry(fallback, defaultPersonality(FishRarity.COMMON, treasure), length));
    }

    private static List<CatchEntry> entriesAt(boolean treasure, FishRarity rarity, FishingHook hook) {
        List<CatchEntry> entries = new ArrayList<>();
        if (treasure) {
            for (ItemStack stack : TREASURE_LOOT.get(rarity)) {
                entries.add(new CatchEntry(stack, defaultPersonality(rarity, true), null));
            }
            if (rarity == FishRarity.RARE) {
                entries.add(new CatchEntry(enchantedBook(hook, Enchantments.LURE), defaultPersonality(rarity, true), null));
            } else if (rarity == FishRarity.EPIC) {
                entries.add(new CatchEntry(enchantedBook(hook, Enchantments.LUCK_OF_THE_SEA), defaultPersonality(rarity, true), null));
            }
        } else {
            for (FishLoot fish : FISH_LOOT.get(rarity)) {
                entries.add(new CatchEntry(fish.stack(), defaultPersonality(rarity, false), fish.length()));
            }
        }

        if (!treasure) {
            FakeItems.FISH.entrySet().stream()
                    .filter(entry -> entry.getValue().personality().rarity() == rarity)
                    .filter(entry -> FishSpawnTag.matches(entry.getValue().spawnTags(), hook.level(), hook.blockPosition()))
                    .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                    .map(FishingCatches::customFishEntry)
                    .forEach(entries::add);
        }
        return entries;
    }

    private static ItemStack enchantedBook(FishingHook hook, ResourceKey<Enchantment> enchantment) {
        Holder<Enchantment> holder = hook.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantment);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(holder, 1);
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static CatchEntry customFishEntry(Map.Entry<FakeItem, FishItemFeature> entry) {
        return new CatchEntry(
                entry.getKey().createItemStack(),
                entry.getValue().personality(),
                entry.getValue().length()
        );
    }

    private static void decorateFish(ItemStack stack, Player catcher, FishSize size, RandomSource random) {
        if (catcher == null || size == null) {
            return;
        }

        double length = size.roll(random);
        List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.add(Component.literal("Caught by: " + catcher.getPlainTextName()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Length: " + formatLength(length)).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(LENGTH_TAG, length));
    }

    private static String formatLength(double length) {
        return String.format(Locale.ROOT, "%.1f cm", length);
    }

    private static FishingPersonality defaultPersonality(FishRarity rarity, boolean treasure) {
        float approachSeconds = treasure ? 1.5F : 0.5F;
        return new FishingPersonality(
                rarity,
                treasure ? 1.0F : 1.5F,
                (treasure ? FishShapes.OBJECT : FishShapes.DEFAULT).value(),
                treasure ? 0.8F : 1.0F,
                treasure ? approachSeconds : 0.2F,
                approachSeconds,
                treasure ? approachSeconds : 1.5F,
                treasure ? 0.6F : 0.75F,
                3.0F
        );
    }

    private static <T> Map<FishRarity, List<T>> emptyLootTable() {
        Map<FishRarity, List<T>> table = new EnumMap<>(FishRarity.class);
        for (FishRarity rarity : FishRarity.values()) {
            table.put(rarity, new ArrayList<>());
        }
        return table;
    }

    private record FishLoot(ItemStack stack, FishSize length) {
    }

    private record CatchEntry(ItemStack stack, FishingPersonality personality, FishSize length) {
    }
}
