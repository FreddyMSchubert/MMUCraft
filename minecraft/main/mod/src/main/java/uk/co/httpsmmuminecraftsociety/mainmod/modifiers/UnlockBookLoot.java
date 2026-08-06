package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GetUnlockAvailabilityResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.UnlockNextKnowledgeResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class UnlockBookLoot {
    private static final long AVAILABILITY_CACHE_MS = 30_000L;
    private static final long AVAILABILITY_TIMEOUT_MS = 750L;
    public static final double FISHING_BOOK_CHANCE = 1.0D / 4.5D;

    private static final List<BookDrop> BOOK_DROPS = List.of(
            new BookDrop("charm-knowledge-book", 33, UnlockAvailabilityType.KNOWLEDGE),
            new BookDrop("charm-fashion-book", 11, UnlockAvailabilityType.COSMETIC),
            new BookDrop("charm-magic-book", 16, UnlockAvailabilityType.CHARM),
            new BookDrop("charm-joke-book", 3, UnlockAvailabilityType.ALWAYS)
    );

    private static final Map<UUID, CacheEntry> AVAILABILITY_CACHE = new ConcurrentHashMap<>();

    private UnlockBookLoot() {
    }

    public static void addBookDrops(Identifier tableId, LootContext lootContext, List<ItemStack> itemStacks) {
        if (!isEligibleLootTable(tableId)) {
            return;
        }
        Entity entity = lootContext.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UnlockAvailability availability = getAvailability(player);

        float charmChance;
        float cosmeticsChance;
        float knowledgeChance;

        if (tableId.toString().contains("mansion"))
            return; // wayy too many mansion chests, would be too op

        boolean isManyChestsStructure = tableId.toString().contains("village") || tableId.toString().contains("end_city");
        boolean isGoodChest = false;
        for (ItemStack stack : itemStacks)
            if (stack.is(ModItemTagProvider.CHARM_DROPPING_CHESTS_HAVE_ITEMS))
                isGoodChest = true;

        if (isManyChestsStructure) {
            cosmeticsChance = 0.15f;
            knowledgeChance = 0.25f;
            charmChance = 0f;
        } else {
            cosmeticsChance = 0.33f;
            knowledgeChance = 0.5f;

            if (isGoodChest)
                charmChance = 0.2f;
            else
                charmChance = 0.002f;
        }

        if (availability.hasCharmsToUnlock() && Math.random() < charmChance)
            addFakeItem("charm-magic-book", itemStacks);
        if (availability.hasCosmeticsToUnlock() && Math.random() < cosmeticsChance)
            addFakeItem("charm-fashion-book", itemStacks);
        if (availability.hasKnowledgeToUnlock() && Math.random() < knowledgeChance)
            addFakeItem("charm-knowledge-book", itemStacks);

        if (Math.random() < 0.42f && availability.hasCosmeticsToUnlock())
            addFakeItem("charm-fashion-book", itemStacks);
    }

    public static void updateAvailability(ServerPlayer player, UnlockNextKnowledgeResponse response) {
        putAvailability(player, UnlockAvailability.from(response));
    }

    public static List<ItemStack> getAvailableUnlockBooks(ServerPlayer player) {
        UnlockAvailability availability = getAvailability(player);
        List<ItemStack> books = new ArrayList<>();
        BOOK_DROPS.stream()
                .filter(book -> book.availabilityType() != UnlockAvailabilityType.ALWAYS)
                .filter(book -> book.isAvailable(availability))
                .forEach(book -> addFakeItem(book.fakeItemId(), books));
        return books;
    }

    public static ItemStack rollFishingBook(ServerPlayer player, RandomSource random) {
        if (random.nextDouble() >= FISHING_BOOK_CHANCE) {
            return ItemStack.EMPTY;
        }

        UnlockAvailability availability = getAvailability(player);
        List<BookDrop> candidates = BOOK_DROPS.stream()
                // The aggressive fishing shortcut is for knowledge; other books retain their normal sources.
                .filter(book -> book.availabilityType() == UnlockAvailabilityType.KNOWLEDGE)
                .filter(book -> book.isAvailable(availability))
                .toList();
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int totalWeight = candidates.stream().mapToInt(BookDrop::weight).sum();
        int remainingWeight = random.nextInt(totalWeight);
        for (BookDrop book : candidates) {
            remainingWeight -= book.weight();
            if (remainingWeight < 0) {
                FakeItem fakeItem = FakeItems.ID_MAP.get(book.fakeItemId());
                if (fakeItem == null) {
                    MainMod.LOGGER.warn("Cannot add fishing unlock-book drop because fake item {} is not loaded", book.fakeItemId());
                    return ItemStack.EMPTY;
                }
                return fakeItem.createItemStack();
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isEligibleLootTable(Identifier tableId) {
        if (tableId == null) {
            return false;
        }

        String path = tableId.getPath();
        return !path.startsWith("entities/") && !path.startsWith("blocks/");
    }

    private static UnlockAvailability getAvailability(ServerPlayer player) {
        long now = System.currentTimeMillis();
        CacheEntry cached = AVAILABILITY_CACHE.get(player.getUUID());
        if (cached != null && cached.isFresh(now)) {
            return cached.availability();
        }

        try {
            GetUnlockAvailabilityResponse response = GameplayGrpcService
                    .getUnlockAvailability(player.getGameProfile().name(), player.getUUID().toString())
                    .get(AVAILABILITY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            UnlockAvailability availability = UnlockAvailability.from(response);
            putAvailability(player, availability);
            return availability;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            MainMod.LOGGER.debug(
                    "Interrupted while refreshing unlock-book loot availability for {}",
                    player.getGameProfile().name(),
                    error
            );
            return cached != null ? cached.availability() : UnlockAvailability.none();
        } catch (Exception error) {
            MainMod.LOGGER.debug(
                    "Failed to refresh unlock-book loot availability for {}",
                    player.getGameProfile().name(),
                    error
            );
            return cached != null ? cached.availability() : UnlockAvailability.none();
        }
    }

    private static void putAvailability(ServerPlayer player, UnlockAvailability availability) {
        AVAILABILITY_CACHE.put(player.getUUID(), new CacheEntry(availability, System.currentTimeMillis()));
    }

    private static void addFakeItem(String fakeItemId, List<ItemStack> itemStacks) {
        FakeItem fakeItem = FakeItems.ID_MAP.get(fakeItemId);
        if (fakeItem == null) {
            MainMod.LOGGER.warn("Cannot add unlock-book loot drop because fake item {} is not loaded", fakeItemId);
            return;
        }

        itemStacks.add(fakeItem.createItemStack());
    }

    private enum UnlockAvailabilityType {
        KNOWLEDGE,
        COSMETIC,
        CHARM,
        ALWAYS
    }

    private record BookDrop(String fakeItemId, int weight, UnlockAvailabilityType availabilityType) {
        private boolean isAvailable(UnlockAvailability availability) {
            return switch (availabilityType) {
                case KNOWLEDGE -> availability.hasKnowledgeToUnlock();
                case COSMETIC -> availability.hasCosmeticsToUnlock();
                case CHARM -> availability.hasCharmsToUnlock();
                case ALWAYS -> true;
            };
        }
    }

    private record UnlockAvailability(
            boolean hasKnowledgeToUnlock,
            boolean hasCosmeticsToUnlock,
            boolean hasCharmsToUnlock
    ) {
        private static UnlockAvailability from(GetUnlockAvailabilityResponse response) {
            return new UnlockAvailability(
                    response.getHasKnowledgeToUnlock(),
                    response.getHasCosmeticsToUnlock(),
                    response.getHasCharmsToUnlock()
            );
        }

        private static UnlockAvailability from(UnlockNextKnowledgeResponse response) {
            return new UnlockAvailability(
                    response.getHasKnowledgeToUnlock(),
                    response.getHasCosmeticsToUnlock(),
                    response.getHasCharmsToUnlock()
            );
        }

        private static UnlockAvailability none() {
            return new UnlockAvailability(false, false, false);
        }
    }

    private record CacheEntry(UnlockAvailability availability, long fetchedAtMs) {
        private boolean isFresh(long nowMs) {
            return nowMs - fetchedAtMs <= AVAILABILITY_CACHE_MS;
        }
    }
}
