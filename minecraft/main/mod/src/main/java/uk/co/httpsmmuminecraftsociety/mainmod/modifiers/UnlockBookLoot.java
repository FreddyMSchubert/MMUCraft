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
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingJumpScares;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GetUnlockAvailabilityResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.UnlockNextKnowledgeResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class UnlockBookLoot {
    private static final long AVAILABILITY_CACHE_MS = 30_000L;
    private static final long AVAILABILITY_TIMEOUT_MS = 750L;
    private static final long CHARM_DROP_COOLDOWN_NANOS = TimeUnit.MINUTES.toNanos(60);
    private static final String CHARM_BOOK_ID = "charm-magic-book";
    private static final double FISHING_KNOWLEDGE_CHANCE = 1.0D / 4.5D;
    private static final double FISHING_JOKE_CHANCE = 0.005D;
    private static final double FISHING_CHARM_MIN_CHANCE = 0.0025D;
    private static final double FISHING_CHARM_MAX_CHANCE = 0.025D;
    private static final double FISHING_COSMETIC_MIN_CHANCE = 0.005D;
    private static final double FISHING_COSMETIC_MAX_CHANCE = 0.0275D;
    private static final double FISHING_DELIVERY_RATE = 1.0D - FishingJumpScares.CHANCE;
    private static long nextCharmDropAtNanos;

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
            addCharmIfAvailable(itemStacks);
        if (availability.hasCosmeticsToUnlock() && Math.random() < cosmeticsChance)
            addFakeItem("charm-fashion-book", itemStacks);
        if (availability.hasKnowledgeToUnlock() && Math.random() < knowledgeChance)
            addFakeItem("charm-knowledge-book", itemStacks);
    }

    public static void updateAvailability(ServerPlayer player, UnlockNextKnowledgeResponse response) {
        putAvailability(player, UnlockAvailability.from(response));
    }

    public static synchronized boolean claimFishingDrop(ItemStack stack) {
        if (!FakeItems.isSpecificFakeItem(stack, CHARM_BOOK_ID)) return true;
        if (!isCharmDropAvailable()) return false;
        startCharmDropCooldown();
        return true;
    }

    public static ItemStack rollFishingBook(ServerPlayer player, RandomSource random, double luck) {
        UnlockAvailability availability = getAvailability(player);
        double progress = Math.max(0.0D, Math.min(11.0D, luck)) / 11.0D;
        double cosmeticChance = FISHING_COSMETIC_MIN_CHANCE
                + (FISHING_COSMETIC_MAX_CHANCE - FISHING_COSMETIC_MIN_CHANCE) * progress;
        double charmChance = FISHING_CHARM_MIN_CHANCE
                + (FISHING_CHARM_MAX_CHANCE - FISHING_CHARM_MIN_CHANCE) * progress;
        double roll = random.nextDouble();
        double cursor = FISHING_KNOWLEDGE_CHANCE;

        if (roll < cursor) return availability.hasKnowledgeToUnlock()
                ? createFishingBook("charm-knowledge-book") : ItemStack.EMPTY;
        cursor += cosmeticChance / FISHING_DELIVERY_RATE;
        if (roll < cursor) return availability.hasCosmeticsToUnlock()
                ? createFishingBook("charm-fashion-book") : ItemStack.EMPTY;
        cursor += charmChance / FISHING_DELIVERY_RATE;
        if (roll < cursor) return availability.hasCharmsToUnlock() && isCharmDropAvailable()
                ? createFishingBook(CHARM_BOOK_ID) : ItemStack.EMPTY;
        cursor += FISHING_JOKE_CHANCE / FISHING_DELIVERY_RATE;
        if (roll < cursor) return createFishingBook("charm-joke-book");
        return ItemStack.EMPTY;
    }

    private static ItemStack createFishingBook(String fakeItemId) {
        FakeItem fakeItem = FakeItems.ID_MAP.get(fakeItemId);
        if (fakeItem != null) return fakeItem.createItemStack();
        MainMod.LOGGER.warn("Cannot add fishing unlock-book drop because fake item {} is not loaded", fakeItemId);
        return ItemStack.EMPTY;
    }

    private static boolean isEligibleLootTable(Identifier tableId) {
        if (tableId == null) {
            return false;
        }

        return tableId.getPath().startsWith("chests/");
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

    private static boolean addFakeItem(String fakeItemId, List<ItemStack> itemStacks) {
        FakeItem fakeItem = FakeItems.ID_MAP.get(fakeItemId);
        if (fakeItem == null) {
            MainMod.LOGGER.warn("Cannot add unlock-book loot drop because fake item {} is not loaded", fakeItemId);
            return false;
        }

        itemStacks.add(fakeItem.createItemStack());
        return true;
    }

    private static synchronized void addCharmIfAvailable(List<ItemStack> itemStacks) {
        if (isCharmDropAvailable() && addFakeItem(CHARM_BOOK_ID, itemStacks)) {
            startCharmDropCooldown();
        }
    }

    private static synchronized boolean isCharmDropAvailable() {
        return System.nanoTime() >= nextCharmDropAtNanos;
    }

    private static synchronized void startCharmDropCooldown() {
        nextCharmDropAtNanos = System.nanoTime() + CHARM_DROP_COOLDOWN_NANOS;
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
