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
import uk.co.httpsmmuminecraftsociety.mainmod.utils.PlayerCooldown;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class UnlockBookLoot {
    private static final long AVAILABILITY_CACHE_MS = 30_000L;
    private static final long AVAILABILITY_TIMEOUT_MS = 750L;
    private static final String CHARM_BOOK_ID = "charm-magic-book";
    private static final double FISHING_KNOWLEDGE_CHANCE = 1.0D / 4.5D;
    private static final double FISHING_JOKE_CHANCE = 0.005D;
    private static final double FISHING_CHARM_MIN_CHANCE = 0.0025D;
    private static final double FISHING_CHARM_MAX_CHANCE = 0.025D;
    private static final double FISHING_COSMETIC_MIN_CHANCE = 0.005D;
    private static final double FISHING_COSMETIC_MAX_CHANCE = 0.0275D;
    private static final double FISHING_DELIVERY_RATE = 1.0D - FishingJumpScares.CHANCE;
    private static final Map<UUID, CacheEntry> AVAILABILITY_CACHE = new ConcurrentHashMap<>();
    private static final PlayerCooldown CHARM_DROP_COOLDOWN = new PlayerCooldown(Duration.ofHours(1));
    private static final PlayerCooldown COSMETIC_DROP_COOLDOWN = new PlayerCooldown(Duration.ofMinutes(30));
    private static final PlayerCooldown KNOWLEDGE_DROP_COOLDOWN = new PlayerCooldown(Duration.ofMinutes(30));

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
        float charmChance;
        float cosmeticsChance;
        float knowledgeChance;
        if (tableId.toString().contains("mansion"))
            return; // wayy too many mansion chests, would be too op

        UnlockAvailability availability = getAvailability(player);

        boolean isManyChestsStructure = tableId.toString().contains("village") || tableId.toString().contains("end_city");
        boolean isGoodChest = itemStacks.stream()
                .anyMatch(stack -> stack.is(ModItemTagProvider.CHARM_DROPPING_CHESTS_HAVE_ITEMS));

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
            addCharmIfAvailable(player, itemStacks);
        if (availability.hasCosmeticsToUnlock() && Math.random() < cosmeticsChance)
            addCosmeticIfAvailable(player, itemStacks);
        if (availability.hasKnowledgeToUnlock() && Math.random() < knowledgeChance)
            addKnowledgeIfAvailable(player, itemStacks);
    }

    public static void updateAvailability(ServerPlayer player, UnlockNextKnowledgeResponse response) {
        putAvailability(player, UnlockAvailability.from(response));
    }

    public static boolean claimFishingDrop(ServerPlayer player, ItemStack stack) {
        if (FakeItems.isSpecificFakeItem(stack, CHARM_BOOK_ID))
            return CHARM_DROP_COOLDOWN.tryStart(player.getUUID());
        if (FakeItems.isSpecificFakeItem(stack, "charm-fashion-book"))
            return COSMETIC_DROP_COOLDOWN.tryStart(player.getUUID());
        if (FakeItems.isSpecificFakeItem(stack, "charm-knowledge-book"))
            return KNOWLEDGE_DROP_COOLDOWN.tryStart(player.getUUID());
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

        if (roll < cursor) return availability.hasKnowledgeToUnlock() && KNOWLEDGE_DROP_COOLDOWN.isReady(player.getUUID())
                ? createFishingBook("charm-knowledge-book") : ItemStack.EMPTY;
        cursor += cosmeticChance / FISHING_DELIVERY_RATE;
        if (roll < cursor) return availability.hasCosmeticsToUnlock() && COSMETIC_DROP_COOLDOWN.isReady(player.getUUID())
                ? createFishingBook("charm-fashion-book") : ItemStack.EMPTY;
        cursor += charmChance / FISHING_DELIVERY_RATE;
        if (roll < cursor) return availability.hasCharmsToUnlock() && CHARM_DROP_COOLDOWN.isReady(player.getUUID())
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

    private static void addCharmIfAvailable(ServerPlayer player, List<ItemStack> itemStacks) {
        FakeItem charmBook = FakeItems.ID_MAP.get(CHARM_BOOK_ID);
        if (charmBook == null) {
            MainMod.LOGGER.warn("Cannot add unlock-book loot drop because fake item {} is not loaded", CHARM_BOOK_ID);
        } else if (CHARM_DROP_COOLDOWN.tryStart(player.getUUID())) {
            itemStacks.add(charmBook.createItemStack());
        }
    }

    private static void addCosmeticIfAvailable(ServerPlayer player, List<ItemStack> itemStacks) {
        FakeItem cosmeticBook = FakeItems.ID_MAP.get("charm-fashion-book");
        if (cosmeticBook == null) {
            MainMod.LOGGER.warn("Cannot add unlock-book loot drop because fake item {} is not loaded", "charm-fashion-book");
        } else if (COSMETIC_DROP_COOLDOWN.tryStart(player.getUUID())) {
            itemStacks.add(cosmeticBook.createItemStack());
        }
    }

    private static void addKnowledgeIfAvailable(ServerPlayer player, List<ItemStack> itemStacks) {
        FakeItem knowledgeBook = FakeItems.ID_MAP.get("charm-knowledge-book");
        if (knowledgeBook == null) {
            MainMod.LOGGER.warn("Cannot add unlock-book loot drop because fake item {} is not loaded", "charm-knowledge-book");
        } else if (KNOWLEDGE_DROP_COOLDOWN.tryStart(player.getUUID())) {
            itemStacks.add(knowledgeBook.createItemStack());
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
