package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public final class MasteryAdvancements {
    private static final int[] COLLECTION_MILESTONES = {1, 3, 5, 10, 20, 40};
    private static final Set<String> BACKPACKS = Set.of("leather", "ingot", "magic", "bejeweled", "withered", "endless");
    private static final int[] SNIFFER_MILESTONES = {1, 3, 5, 10, 20, 30, 50, 100, 200, 500, 1000, 5000, 10000};
    private MasteryAdvancements() {}

    public static boolean grant(ServerPlayer player, String path) {
        return grantId(player, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mastery/" + path));
    }

    public static boolean grantId(ServerPlayer player, Identifier id) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id);
        if (holder == null) return false;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        boolean granted = false;
        for (String criterion : progress.getRemainingCriteria()) {
            granted |= player.getAdvancements().award(holder, criterion);
        }
        return granted;
    }

    public static void onJoin(ServerPlayer player) {
        grant(player, "root");
        int hour = ZonedDateTime.now().getHour();
        if (hour >= 2 && hour <= 8) grant(player, "social/join_" + hour);
        if (allDone(player, "social/join_", 2, 8)) grant(player, "social/unreasonable_hours");
        scanInventory(player);
    }

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 == 0) {
            grant(player, "root");
            scanInventory(player);
            if (player.getVehicle() instanceof AbstractMinecart minecart
                    && minecart.getDeltaMovement().horizontalDistance() * 20 >= 19.9) {
                grant(player, "utility/full_speed_minecart");
            }
        }
    }

    public static void grantIfAll(ServerPlayer player, String result, String... prerequisites) {
        for (String prerequisite : prerequisites) {
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(
                    Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mastery/" + prerequisite));
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) return;
        }
        grant(player, result);
    }

    public static void recordSnifferBreed(ServerPlayer player) {
        grantMilestones(player, "mmuSniffers", "sniffers/bred_", SNIFFER_MILESTONES);
    }

    public static void recordCharmUpgrade(ServerPlayer player) {
        grantMilestones(player, "mmuCharmUpgrades", "charms/upgrade_", new int[]{3, 10, 25, 50});
    }

    public static void recordCosmeticPurchase(ServerPlayer player, String rarity) {
        grant(player, "cosmetics/buy_" + rarity.toLowerCase(java.util.Locale.ROOT));
        grantMilestones(player, "mmuCosmeticsBought", "cosmetics/buy_", new int[]{1, 5, 10, 20, 50, 100});
        long cosmeticTotal = FakeItems.ALL.stream()
                .filter(item -> item.getFeature(EquippableCosmeticItemFeature.class) != null)
                .count();
        Objective objective = player.level().getServer().getScoreboard().getObjective("mmuCosmeticsBought");
        if (objective != null && player.level().getServer().getScoreboard()
                .getOrCreatePlayerScore(player, objective).get() >= cosmeticTotal) {
            grant(player, "cosmetics/buy_all");
        }
    }

    private static void grantMilestones(ServerPlayer player, String objectiveName, String pathPrefix, int[] milestones) {
        ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    objectiveName,
                    ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.empty(),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }
        int count = scoreboard.getOrCreatePlayerScore(player, objective).increment();
        for (int milestone : milestones) {
            if (count >= milestone) grant(player, pathPrefix + milestone);
        }
    }

    private static void scanInventory(ServerPlayer player) {
        Set<String> fakeIds = new HashSet<>();
        Set<Integer> charmIds = new HashSet<>();
        Set<String> cosmetics = new HashSet<>();
        Set<String> maxedCharmIds = new HashSet<>();
        boolean enderiteHelmet = false;
        boolean enderiteChestplate = false;
        boolean enderiteLeggings = false;
        boolean enderiteBoots = false;

        for (ItemStack stack : player.getInventory()) {
            if (stack.is(Items.ELYTRA) && !GliderCharm.isGlider(stack)) {
                grantId(player, Identifier.parse("minecraft:end/elytra"));
            }
            if (CharmorManager.isEnderite(stack)) {
                if (stack.is(Items.DIAMOND_HOE) || stack.is(Items.NETHERITE_HOE)) grant(player, "enderite/hoe");
                enderiteHelmet |= stack.is(Items.DIAMOND_HELMET) || stack.is(Items.NETHERITE_HELMET);
                enderiteChestplate |= stack.is(Items.DIAMOND_CHESTPLATE) || stack.is(Items.NETHERITE_CHESTPLATE);
                enderiteLeggings |= stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.NETHERITE_LEGGINGS);
                enderiteBoots |= stack.is(Items.DIAMOND_BOOTS) || stack.is(Items.NETHERITE_BOOTS);
            }
            if (ParticleTrailData.getTrailSpec(stack).totalWeight(true) > 0) {
                grant(player, "utility/particle_trail");
            }
            CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (model == null || model.strings().isEmpty()) continue;
            String id = model.strings().getFirst();
            FakeItem fakeItem = FakeItems.ID_MAP.get(id);
            if (fakeItem == null) continue;
            fakeIds.add(id);
            CharmsManager.getCharmInstances(stack).forEach(charm -> {
                charmIds.add(charm.charmId());
                if (charm.level() >= charm.feature().maxLevel()) maxedCharmIds.add(charm.fakeItem().id());
            });
            if (fakeItem.getFeature(EquippableCosmeticItemFeature.class) != null) {
                cosmetics.add(id);
                grant(player, "cosmetics/find_" + rarityName(fakeItem.rarity()));
            }
        }

        for (int milestone : COLLECTION_MILESTONES) {
            if (charmIds.size() >= milestone) grant(player, "charms/get_" + milestone);
        }
        if (charmIds.size() >= FakeItems.CHARM_ID_MAP.size()) grant(player, "charms/get_all");
        if (maxedCharmIds.size() >= FakeItems.CHARM_ID_MAP.size()) grant(player, "charms/upgrade_all_max");
        for (int milestone : new int[]{5, 10, 20, 50, 100, 200}) {
            if (cosmetics.size() >= milestone) grant(player, "cosmetics/collect_" + milestone);
        }
        long cosmeticTotal = FakeItems.ALL.stream()
                .filter(item -> item.getFeature(EquippableCosmeticItemFeature.class) != null)
                .count();
        if (cosmetics.size() >= cosmeticTotal) grant(player, "cosmetics/collect_all");
        if (enderiteHelmet && enderiteChestplate && enderiteLeggings && enderiteBoots) {
            grant(player, "enderite/armor");
        }

        if (fakeIds.contains("charm-wallet")) grant(player, "utility/wallet");
        if (fakeIds.contains("charm-glider")) grant(player, "glider/craft");
        if (fakeIds.contains("cosmetic-crown-royal")) grant(player, "cosmetics/royal_crown");
        if (fakeIds.contains("cosmetic-amogus")) grant(player, "cosmetics/amogus");
        if (cosmetics.stream().filter(id -> id.startsWith("cosmetic-villager-") || id.equals("cosmetic-witch-hat")).count() >= 15) {
            grant(player, "cosmetics/all_villager_hats");
        }
        for (String tier : BACKPACKS) {
            if (fakeIds.contains("charm-" + tier + "-backpack")) grant(player, "backpacks/" + tier);
        }
        if (fakeIds.contains("enderite-scrap")) grant(player, "enderite/scrap");
        if (fakeIds.contains("enderite-ingot")) grant(player, "enderite/ingot");
        if (fakeIds.contains("enderite-upgrade-smithing-template")) grant(player, "enderite/template");
        for (int value : new int[]{1000, 10000, 100000, 1000000}) {
            if (fakeIds.contains("coin-" + value)) grant(player, "coins/hold_" + value);
        }
    }

    private static String rarityName(Rarity rarity) {
        if (rarity == Rarity.UNCOMMON) return "uncommon";
        if (rarity == Rarity.RARE) return "rare";
        if (rarity == Rarity.EPIC) return "epic";
        return "common";
    }

    private static boolean allDone(ServerPlayer player, String prefix, int first, int last) {
        for (int value = first; value <= last; value++) {
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(
                    Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mastery/" + prefix + value));
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) return false;
        }
        return true;
    }
}
