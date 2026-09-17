package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider.GliderCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.GiantsBootsCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class MasteryAdvancements {
    private static final ZoneId BRITISH_TIME = ZoneId.of("Europe/London");
    private static final int[] COLLECTION_MILESTONES = {1, 3, 5, 10, 15, 20};
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
        int hour = ZonedDateTime.now(BRITISH_TIME).getHour();
        if (hour >= 2 && hour <= 6) {
            grant(player, "social/join_insane_hour");
            grant(player, "social/join_" + hour);
        }
        if (allDone(player, "social/join_", 2, 6)) grant(player, "social/unreasonable_hours");
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
        grantMilestones(player, "mmuCharmUpgrades", "charms/upgrade_", new int[]{3, 5, 10, 15, 20});
    }

    public static void recordCosmeticPurchase(ServerPlayer player, String rarity, String itemId) {
        grant(player, "cosmetics/buy_" + rarity.toLowerCase(java.util.Locale.ROOT));
        grantMilestones(player, "mmuCosmeticsBought", "cosmetics/buy_", new int[]{1, 5, 10, 20, 50, 100});
        switch (itemId) {
            case "cosmetic-dog-pet-hat" -> grant(player, "cosmetics/good_boy");
            case "cosmetic-academic-hat" -> grant(player, "cosmetics/academic_hat");
            case "cosmetic-villager-nose" -> grant(player, "cosmetics/villager_nose");
            case "cosmetic-villager-farmer" -> grant(player, "cosmetics/straw_hat_farmer");
            case "cosmetic-sun-hat" -> grant(player, "cosmetics/straw_hat_sun");
            case "cosmetic-villager-fletcher" -> grant(player, "cosmetics/fletcher_hat");
            case "cosmetic-posh-squid" -> grant(player, "cosmetics/posh_squid");
            case "cosmetic-helmet-retro" -> grant(player, "cosmetics/retro_helmet");
            case "cosmetic-hood-bee-trans" -> grant(player, "cosmetics/trans_bee_hood");
            case "cosmetic-sombrero" -> grant(player, "cosmetics/sombrero");
            case "cosmetic-sombreron" -> grant(player, "cosmetics/sombreron");
            case "cosmetic-sombreronn" -> grant(player, "cosmetics/sombreronn");
            case "cosmetic-crown-techno" -> grant(player, "cosmetics/crown_techno");
            case "cosmetic-crown-royal" -> grant(player, "cosmetics/crown_royal");
            case "cosmetic-crown-ornate" -> grant(player, "cosmetics/crown_ornate");
            default -> { }
        }
        grantIfAll(player, "cosmetics/all_straw_hats",
                "cosmetics/straw_hat_farmer",
                "cosmetics/straw_hat_sun",
                "cosmetics/sombrero",
                "cosmetics/sombreron",
                "cosmetics/sombreronn");
        long cosmeticTotal = FakeItems.ALL.stream()
                .filter(item -> item.getFeature(EquippableCosmeticItemFeature.class) != null)
                .count();
        Objective objective = player.level().getServer().getScoreboard().getObjective("mmuCosmeticsBought");
        if (objective != null && player.level().getServer().getScoreboard()
                .getOrCreatePlayerScore(player, objective).get() >= cosmeticTotal) {
            grant(player, "cosmetics/buy_all");
        }
    }

    public static void checkBalance(ServerPlayer player, int balance) {
        for (int value : new int[]{100, 1000, 10000, 100000, 1000000}) {
            if (balance >= value) grant(player, "money/balance_" + value);
        }
    }

    public static void recordNewJoke(ServerPlayer player) {
        grantMilestones(player, "mmuJokesRead", "utility/jokes_", new int[]{1, 3, 5});
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
            if (fakeItem.getFeature(EquippableCharmItemFeature.class) != null) {
                CharmsManager.getCharmInstances(stack).forEach(charm -> {
                    charmIds.add(charm.charmId());
                    if (charm.level() >= charm.feature().maxLevel()) maxedCharmIds.add(charm.fakeItem().id());
                });
            }
        }

        for (int milestone : COLLECTION_MILESTONES) {
            if (charmIds.size() >= milestone) grant(player, "charms/get_" + milestone);
        }
        long equippableCharmTotal = FakeItems.ALL.stream()
                .filter(item -> item.getFeature(EquippableCharmItemFeature.class) != null)
                .count();
        if (charmIds.size() >= equippableCharmTotal) grant(player, "charms/get_all");
        if (maxedCharmIds.size() >= equippableCharmTotal) grant(player, "charms/upgrade_all_max");
        if (enderiteHelmet && enderiteChestplate && enderiteLeggings && enderiteBoots) {
            grant(player, "enderite/armor");
        }

        if (fakeIds.contains("charm-wallet")) grant(player, "utility/wallet");
        if (fakeIds.contains("charm-glider")) grant(player, "glider/craft");
        for (String tier : BACKPACKS) {
            if (fakeIds.contains("charm-" + tier + "-backpack")) grant(player, "backpacks/" + tier);
        }
        if (fakeIds.contains("enderite-scrap")) grant(player, "enderite/scrap");
        if (fakeIds.contains("enderite-ingot")) grant(player, "enderite/ingot");
        if (fakeIds.contains("enderite-upgrade-smithing-template")) grant(player, "enderite/template");
        for (int value : new int[]{1, 10, 100, 1000, 10000, 100000, 1000000}) {
            if (fakeIds.contains("coin-" + value)) grant(player, "coins/hold_" + value);
        }
        if (isCatCosmetic(player.getMainHandItem())
                && isCatCosmetic(player.getOffhandItem())
                && isCatCosmetic(player.getItemBySlot(EquipmentSlot.HEAD))) {
            grant(player, "cosmetics/cat_stack");
        }
        if (CharmsManager.hasAbility(player.getItemBySlot(EquipmentSlot.FEET), GiantsBootsCharm.class)
                && FakeItems.isSpecificFakeItem(
                        player.getItemBySlot(EquipmentSlot.HEAD),
                        "cosmetic-sombreronn"
                )) {
            grant(player, "cosmetics/solar_eclipse");
        }
    }

    private static boolean isCatCosmetic(ItemStack stack) {
        FakeItem item = FakeItems.getFakeItemFromStack(stack);
        return item != null && item.id().matches("cosmetic-(calico|red|siamese|tabby|tuxedo|white)-cat-pet-hat");
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
