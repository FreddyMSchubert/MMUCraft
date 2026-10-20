package uk.co.httpsmmuminecraftsociety.mainmod.ducks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ApplyDuckUpdateRequest;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.ApplyDuckUpdateResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.DuckAward;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.SyncPlayerStatsResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DuckManager {
    private static final long HUNDRED_HOURS_TICKS = 100L * 60L * 60L * 20L;
    private static final DateTimeFormatter AWARD_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu")
            .withZone(ZoneId.of("Europe/London"));
    private static final Map<UUID, Integer> lastSensorMask = new HashMap<>();
    private static final Set<String> deliveredThisSession = new HashSet<>();
    private static final Set<String> announcedThisSession = new HashSet<>();

    private DuckManager() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                lastSensorMask.put(handler.player.getUUID(), sensorMask(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            lastSensorMask.remove(handler.player.getUUID());
            deliveredThisSession.removeIf(key -> key.startsWith(handler.player.getUUID() + ":"));
            announcedThisSession.removeIf(key -> key.startsWith(handler.player.getUUID() + ":"));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int current = sensorMask(player);
                Integer previous = lastSensorMask.put(player.getUUID(), current);
                if (previous == null || !FeatureToggles.isEnabled(FeatureToggles.NETHER)) continue;
                int gained = current & ~previous;
                for (int id : new int[] {5, 6, 7, 8, 9}) {
                    if ((gained & (1 << id)) == 0) continue;
                    record(player, id).exceptionally(error -> {
                        server.execute(() -> lastSensorMask.computeIfPresent(player.getUUID(),
                                (uuid, mask) -> mask & ~(1 << id)));
                        return null;
                    });
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim
                    && source.getEntity() instanceof ServerPlayer killer
                    && PlayerStatsSync.isCommittee(victim)
                    && FeatureToggles.isEnabled(FeatureToggles.NETHER)) {
                record(killer, 4);
            }
        });
    }

    public static java.util.concurrent.CompletableFuture<?> record(ServerPlayer player, int duckId) {
        if (!FeatureToggles.isEnabled(FeatureToggles.NETHER))
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        return GameplayGrpcService.recordDuckEvent(player, duckId).exceptionally(error -> {
            MainMod.LOGGER.warn("Could not record duck {} for {}", duckId, player.getScoreboardName(), error);
            throw new RuntimeException(error);
        });
    }

    public static ApplyDuckUpdateResponse applyUpdate(ApplyDuckUpdateRequest update) {
        MinecraftServer server = uk.co.httpsmmuminecraftsociety.mainmod.grpc.GrpcBridge.minecraftServer();
        if (server == null) return ApplyDuckUpdateResponse.newBuilder().setApplied(false).build();
        ServerPlayer player = server.getPlayerList().getPlayerByName(update.getMinecraftUsername());
        if (player != null && !player.hasDisconnected()) PlayerStatsSync.applyDuckCount(player, update.getDuckCount());
        for (DuckAward award : update.getNewAwardsList()) {
            announce(server, player, award);
            if (player != null && !player.hasDisconnected()) deliver(player, award, false);
        }
        return ApplyDuckUpdateResponse.newBuilder().setApplied(true).build();
    }

    public static void onSync(ServerPlayer player, SyncPlayerStatsResponse response, boolean applyCount) {
        if (!response.getAccountLinked()) return;
        if (applyCount) PlayerStatsSync.applyDuckCount(player, response.getDuckCount());
        for (DuckAward award : response.getDuckNewAwardsList()) announce(player.level().getServer(), player, award);
        Set<Integer> newIds = new HashSet<>();
        for (DuckAward award : response.getDuckNewAwardsList()) newIds.add(award.getDuckId());
        for (DuckAward award : response.getDuckPendingItemsList()) {
            deliver(player, award, !newIds.contains(award.getDuckId()));
        }
    }

    private static int sensorMask(ServerPlayer player) {
        int mask = 0;
        if (fullCoinInventory(player)) mask |= 1 << 5;
        if (wearingMythicalCosmetic(player)) mask |= 1 << 6;
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) >= HUNDRED_HOURS_TICKS)
            mask |= 1 << 7;
        if (player.getMaxHealth() == 40.0F && player.getHealth() >= 40.0F) mask |= 1 << 8;
        if (fullEnderite(player)) mask |= 1 << 9;
        return mask;
    }

    private static boolean fullCoinInventory(ServerPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            if (!fullOneDabloonStack(player.getInventory().getItem(slot))) return false;
        }
        return fullOneDabloonStack(player.getOffhandItem());
    }

    private static boolean fullOneDabloonStack(ItemStack stack) {
        return !stack.isEmpty() && WalletCharm.isCoin(stack) == 1
                && stack.getCount() == stack.getMaxStackSize();
    }

    private static boolean wearingMythicalCosmetic(ServerPlayer player) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (mythicalCosmetic(armor) || mythicalCosmetic(CosmeticsManager.cosmeticFromHelmetReplica(armor)))
                return true;
        }
        return false;
    }

    private static boolean mythicalCosmetic(ItemStack stack) {
        FakeItem item = FakeItems.getFakeItemFromStack(stack);
        return item != null && "mythical".equals(item.rarityKey())
                && item.getFeature(EquippableCosmeticItemFeature.class) != null;
    }

    private static boolean fullEnderite(ServerPlayer player) {
        return CharmorManager.isEnderite(player.getItemBySlot(EquipmentSlot.HEAD))
                && CharmorManager.isEnderite(player.getItemBySlot(EquipmentSlot.CHEST))
                && CharmorManager.isEnderite(player.getItemBySlot(EquipmentSlot.LEGS))
                && CharmorManager.isEnderite(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static void announce(MinecraftServer server, ServerPlayer player, DuckAward award) {
        boolean finalDuck = award.getDuckId() == 13;
        String reason = "earned the " + award.getName() + " for " + award.getAnnouncementReason()
                + "! ✦ (" + award.getCount() + "/13 ducks collected)";
        Component prefix = Component.literal(finalDuck ? "[FINAL DUCK] " : "[DUCK] ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component ownerMessage = Component.empty().append(prefix.copy())
                .append(Component.literal("You " + reason).withStyle(ChatFormatting.YELLOW));
        Component publicMessage = Component.empty().append(prefix.copy())
                .append(player == null ? offlineName(award) : player.getDisplayName())
                .append(Component.literal(" " + reason).withStyle(ChatFormatting.YELLOW));
        DiscordBridge.broadcastDuck(server, player, ownerMessage, publicMessage);
        if (player != null && !player.hasDisconnected()) {
            announcedThisSession.add(awardKey(player, award));
            if (finalDuck) celebrate(player);
        }
    }

    private static Component offlineName(DuckAward award) {
        String color = award.getColorHex();
        if (color.matches("#[0-9a-fA-F]{6}")) {
            return Component.literal(award.getAwardedTo()).withStyle(style ->
                    style.withColor(TextColor.fromRgb(Integer.parseInt(color.substring(1), 16))));
        }
        return Component.literal(award.getAwardedTo());
    }

    private static String awardKey(ServerPlayer player, DuckAward award) {
        return player.getUUID() + ":" + award.getDuckId() + ":" + award.getAwardedAtUnixMs();
    }

    private static void celebrate(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.4,
                    player.getZ(), 100, 0.8, 0.8, 0.8, 0.15);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.5F, 1.0F);
        }
    }

    private static void deliver(ServerPlayer player, DuckAward award, boolean reminder) {
        if (player.hasDisconnected()) return;
        String key = awardKey(player, award);
        if (deliveredThisSession.contains(key)) {
            ack(player, award);
            return;
        }
        if (!FakeItems.isKnownFakeItem(award.getItemId())) return;
        ItemStack stack = FakeItems.createFakeItemStack(award.getItemId(), 1);
        personalize(stack, award.getAwardedTo(), award.getAwardedAtUnixMs(), award.getCondition());
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Make one empty inventory slot to receive your free "
                    + award.getName() + ". It will be retried automatically.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        deliveredThisSession.add(key);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (reminder && !announcedThisSession.contains(key)) {
            boolean finalDuck = award.getDuckId() == 13;
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal(finalDuck ? "[FINAL DUCK] " : "[DUCK DELIVERY] ")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(finalDuck
                            ? "While you were away, you completed the pond! Your free final duck is in your inventory. ✦"
                            : "While you were away, you earned the " + award.getName() + " for "
                                    + award.getAnnouncementReason() + "! Your free duck is in your inventory. ✦")
                            .withStyle(ChatFormatting.YELLOW)));
            if (finalDuck) celebrate(player);
        }
        ack(player, award);
    }

    private static void ack(ServerPlayer player, DuckAward award) {
        GameplayGrpcService.ackDuckDelivery(player, award).exceptionally(error -> {
            MainMod.LOGGER.warn("Could not confirm delivery of duck {} to {}", award.getDuckId(),
                    player.getScoreboardName(), error);
            return null;
        });
    }

    public static void personalize(ItemStack stack, String awardedTo, long awardedAtUnixMs, String condition) {
        if (stack.isEmpty() || awardedTo.isBlank() || awardedAtUnixMs <= 0) return;
        List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.add(Component.literal("Awarded to " + awardedTo).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Awarded on " + AWARD_DATE.format(Instant.ofEpochMilli(awardedAtUnixMs)))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(condition).withStyle(ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }
}
