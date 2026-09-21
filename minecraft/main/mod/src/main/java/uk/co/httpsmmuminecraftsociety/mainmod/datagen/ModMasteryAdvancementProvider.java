package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.CommitteeMembers;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.DyeableItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCosmeticItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishRarity;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishSpawnTag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ModMasteryAdvancementProvider extends FabricAdvancementProvider {
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, AdvancementHolder> saved = new HashMap<>();

    public ModMasteryAdvancementProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registries, Consumer<AdvancementHolder> consumer) {
        defineAdvancements();
        for (String path : definitions.keySet()) {
            int depth = 0;
            for (String current = path; current != null; current = definitions.get(current).parent) {
                if (++depth > 12) throw new IllegalStateException("Mastery branch exceeds 12 levels: " + path);
                if (!definitions.containsKey(current)) throw new IllegalStateException("Unknown mastery parent: " + current);
            }
            save(path, consumer);
        }
    }

    private AdvancementHolder save(String path, Consumer<AdvancementHolder> consumer) {
        AdvancementHolder existing = saved.get(path);
        if (existing != null) return existing;
        Definition definition = definitions.get(path);
        if (definition == null) throw new IllegalStateException("Unknown mastery parent: " + path);
        Advancement.Builder builder = new Advancement.Builder();
        if (definition.parent != null) builder.parent(save(definition.parent, consumer));
        builder.display(new DisplayInfo(icon(definition.icon), Component.literal(definition.title),
                Component.literal(definition.description),
                path.equals("root") ? Optional.of(new ClientAsset.ResourceTexture(Identifier.parse("minecraft:block/enderite_block"))) : Optional.empty(),
                AdvancementType.valueOf(definition.frame.toUpperCase(java.util.Locale.ROOT)),
                !path.equals("root"), !path.equals("root"), hidden(path)));
        builder.addCriterion("done", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()));
        AdvancementHolder holder = builder.save(consumer, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mastery/" + path));
        saved.put(path, holder);
        return holder;
    }

    private static ItemStackTemplate icon(String spec) {
        boolean glint = spec.endsWith(":glint");
        if (glint) spec = spec.substring(0, spec.length() - 6);
        DataComponentPatch.Builder components = DataComponentPatch.builder();
        if (spec.startsWith("head:")) {
            components.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(spec.substring(5)));
            return new ItemStackTemplate(Items.PLAYER_HEAD, components.build());
        }
        if (spec.startsWith("fake:")) {
            String fakeId = spec.substring(5);
            boolean fullPhial = fakeId.equals("charm-sculk-phial-full");
            if (fullPhial) fakeId = "charm-sculk-phial";
            var fakeItem = FakeItems.requireFakeItem(fakeId);
            components.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    fullPhial ? List.of(1395.0F) : List.of(), List.of(), List.of(fakeId), List.of()));
            DyeableItemFeature dyeable = fakeItem.getFeature(DyeableItemFeature.class);
            if (dyeable != null) components.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeable.dyeColor()));
            if (glint) components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            return new ItemStackTemplate(fakeItem.baseItem(), components.build());
        }
        String vanillaId = spec.startsWith("enderite:") ? "minecraft:" + spec.substring(9) : spec;
        if (spec.startsWith("enderite:")) {
            components.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(true), List.of(), List.of()));
        }
        if (glint) components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return new ItemStackTemplate(BuiltInRegistries.ITEM.getValue(Identifier.parse(vanillaId)), components.build());
    }

    private static boolean hidden(String path) {
        if (path.startsWith("cosmetics/")) {
            return !path.startsWith("cosmetics/unlock_") && !path.startsWith("cosmetics/buy_")
                    && !path.equals("cosmetics/all_straw_hats") && !path.equals("cosmetics/all_villager_hats")
                    && !path.equals("cosmetics/cat_stack") && !path.equals("cosmetics/solar_eclipse");
        }
        return path.startsWith("fishing/species/") || path.equals("fishing/matrix_fish") || path.equals("fishing/spook_fish");
    }

    private void add(String path, String title, String description, String icon, String parent, String frame) {
        if (definitions.putIfAbsent(path, new Definition(parent, title, description, icon, frame)) != null) {
            throw new IllegalStateException("Duplicate mastery advancement: " + path);
        }
    }

    private void chain(String prefix, int[] values, String title, String description, String icon, String parent) {
        String previous = parent;
        for (int value : values) {
            String path = prefix + "_" + value;
            add(path, title + " " + value, description.replace("%s", Integer.toString(value)), icon,
                    previous, value >= 100 ? "challenge" : value >= 10 ? "goal" : "task");
            previous = path;
        }
    }

    private void reparent(String path, String parent) {
        Definition definition = definitions.get(path);
        definitions.put(path, new Definition(parent, definition.title, definition.description, definition.icon, definition.frame));
    }

    private void line(String parent, String... paths) {
        for (String path : paths) {
            reparent(path, parent);
            parent = path;
        }
    }

    private void defineAdvancements() {
        add("root", "Welcome! Your journey starts now.", "Master every corner of MMUCraft.", "fake:deco-mmu-dirt", null, "task");
        add("fishing/first_catch", "Something's Fishy", "Catch a fish.", "minecraft:fishing_rod", "root", "task");
        add("fishing/common", "Common Catch", "Catch a common fish.", "minecraft:fishing_rod", "fishing/first_catch", "task");
        add("fishing/uncommon", "Uncommon Catch", "Catch an uncommon fish.", "minecraft:fishing_rod", "fishing/common", "task");
        add("fishing/rare", "Rare Catch", "Catch a rare fish.", "minecraft:fishing_rod", "fishing/uncommon", "goal");
        add("fishing/epic", "Epic Catch", "Catch an epic fish.", "minecraft:fishing_rod", "fishing/rare", "goal");
        add("fishing/legendary", "Legendary Catch", "Catch a legendary fish.", "minecraft:fishing_rod", "fishing/epic", "challenge");
        add("fishing/mythical", "Mythical Catch", "Catch a mythical fish.", "minecraft:fishing_rod", "fishing/legendary", "challenge");
        add("fishing/acoustic_bass", "Unplugged", "Catch an Acoustic Bass.", "fake:fish-acousticbass", "fishing/common", "task");
        add("fishing/goldfish", "Worth Its Weight", "Catch a Goldfish.", "fake:fish-goldfish", "fishing/common", "task");
        add("fishing/vampire_carp", "Love at First Bite", "Catch a Vampire Carp.", "fake:fish-vampirecarp", "fishing/common", "task");
        add("fishing/thundering_bass", "Drop the Bass", "Catch a Thundering Bass.", "fake:fish-thundering_bass", "fishing/common", "task");
        add("fishing/swordfish", "Mightier Than the Pen", "Catch a Swordfish.", "fake:fish-swordfish", "fishing/common", "task");
        add("fishing/rainbow_trout", "Over the Rainbow", "Catch a Rainbow Trout.", "fake:fish-rainbow_trout", "fishing/uncommon", "task");
        add("fishing/galaxy_starfish", "A Whole Galaxy", "Catch a Galaxy Starfish.", "fake:fish-galaxy_starfish", "fishing/uncommon", "task");
        add("fishing/thunderfin", "Greased Lightning", "Catch a Thunderfin.", "fake:fish-thunderfin", "fishing/uncommon", "task");
        add("fishing/skyfish", "The Sky's the Limit", "Catch a Skyfish.", "fake:fish-skyfish", "fishing/uncommon", "task");
        add("fishing/baguette_fish", "Baguette About It", "Catch a Baguette Fish.", "fake:fish-baguettefish", "fishing/rare", "goal");
        add("fishing/nebula_swordfish", "Written in the Stars", "Catch a Nebula Swordfish.", "fake:fish-nebula_swordfish", "fishing/rare", "goal");
        add("fishing/witchfish", "Something Wicked", "Catch a Witchfish.", "fake:fish-witchfish", "fishing/rare", "goal");
        add("fishing/freddy_fish", "Five Nights at Fishing", "Catch a Freddy Fish.", "fake:fish-freddyfish", "fishing/epic", "goal");
        add("fishing/charged_thunderfin", "Fully Charged", "Catch a Charged Thunderfin.", "fake:fish-charged_thunderfin", "fishing/epic", "goal");
        add("fishing/matrix_fish", "There Is No Spoon", "Catch a Matrix Fish.", "fake:fish-matrix_fish", "fishing/legendary", "challenge");
        add("fishing/spook_fish", "Ghost in the Water", "Catch a Spook Fish.", "fake:fish-spook_fish", "fishing/mythical", "challenge");
        add("fishing/all_common", "Common Knowledge", "Catch every common fish.", "minecraft:fishing_rod:glint", "fishing/common", "goal");
        add("fishing/all_uncommon", "Uncommonly Thorough", "Catch every uncommon fish.", "minecraft:fishing_rod:glint", "fishing/uncommon", "goal");
        add("fishing/all_rare", "Rare Completionist", "Catch every rare fish.", "minecraft:fishing_rod:glint", "fishing/rare", "challenge");
        add("fishing/all_epic", "An Epic Catalogue", "Catch every epic fish.", "minecraft:fishing_rod:glint", "fishing/epic", "challenge");
        add("fishing/all_legendary", "Legends Collected", "Catch every legendary fish.", "minecraft:fishing_rod:glint", "fishing/legendary", "challenge");
        add("fishing/all_mythical", "Myths Made Real", "Catch every mythical fish.", "minecraft:fishing_rod:glint", "fishing/mythical", "challenge");
        add("fishing/full_compendium", "The Full Compendium", "Catch every fish in MMUCraft.", "fake:charm-knowledge-book", "fishing/all_mythical", "challenge");
        add("fishing/brush_modifier", "Something in the Sand", "Brush a fishing modifier from suspicious sand or gravel.", "minecraft:brush", "fishing/first_catch", "task");
        add("fishing/worm", "Hook, Line and Worm", "Catch a fish with a Worm in your off hand.", "fake:worms", "fishing/brush_modifier", "task");
        add("fishing/golden_worm", "Golden Bait", "Catch a fish with a Golden Worm in your off hand.", "fake:golden-worms", "fishing/worm", "goal");
        add("fishing/magnet", "Magnetic Personality", "Catch a fish with an Item Magnet in your off hand.", "fake:item-magnet", "fishing/brush_modifier", "goal");
        add("fishing/golden_magnet", "Attractive Prospect", "Catch a fish with a Golden Item Magnet in your off hand.", "fake:golden-item-magnet", "fishing/magnet", "challenge");
        add("fishing/luck_3", "Luck of the Sea III", "Catch a fish with at least three points of fishing luck.", "fake:charm-lucky-charm", "fishing/first_catch", "goal");
        add("fishing/server_smallest_record", "Small Fry", "Set a server record for the smallest specimen of a fish.", "fake:fish-bluegill", "fishing/first_catch", "goal");
        add("fishing/server_largest_record", "The One That Was This Big", "Set a server record for the largest specimen of a fish.", "fake:fish-bluegill", "fishing/server_smallest_record", "goal");
        add("cosmetics/amogus", "Suspiciously Stylish", "Buy the Amogus Hat.", "fake:cosmetic-amogus", "cosmetics/buy_1", "goal");
        add("cosmetics/villager_nose", "Hrrrm", "Buy the Villager Nose.", "fake:cosmetic-villager-nose", "cosmetics/buy_1", "task");
        add("cosmetics/all_villager_hats", "The Entire Workforce", "Unlock every villager profession hat and the Witch Hat.", "fake:cosmetic-witch-hat", "cosmetics/unlock_1", "challenge");
        add("cosmetics/good_boy", "Good Boy", "Buy the Dog Pet Hat.", "fake:cosmetic-dog-pet-hat", "cosmetics/buy_1", "goal");
        add("cosmetics/academic_hat", "Eventually Graduating", "Buy the Academic Hat. Minecraft is excellent revision, probably.", "fake:cosmetic-academic-hat", "cosmetics/buy_1", "goal");
        add("cosmetics/straw_hat_farmer", "Working in the Sun", "Buy the Farmer Straw Hat.", "fake:cosmetic-villager-farmer", "cosmetics/buy_1", "task");
        add("cosmetics/straw_hat_sun", "Here Comes the Sun", "Buy the Sun Hat.", "fake:cosmetic-sun-hat", "cosmetics/buy_1", "task");
        add("cosmetics/all_straw_hats", "Straw Hat Collection", "Buy the Farmer Straw Hat, Sun Hat, Sombrero, Sombreron, and Sombreronn.", "fake:cosmetic-sombreronn", "cosmetics/buy_1", "goal");
        add("cosmetics/fletcher_hat", "Straight and Arrow", "Buy the Fletcher Hat.", "fake:cosmetic-villager-fletcher", "cosmetics/villager_nose", "task");
        add("cosmetics/posh_squid", "Poor Squid", "Buy the Posh Squid.", "fake:cosmetic-posh-squid", "cosmetics/buy_1", "goal");
        add("cosmetics/retro_helmet", "Retrofuturism", "Buy the Retro Helmet.", "fake:cosmetic-helmet-retro", "cosmetics/buy_1", "task");
        add("cosmetics/trans_bee_hood", "Bee Yourself", "Buy the Trans Bee Hood.", "fake:cosmetic-hood-bee-trans", "cosmetics/buy_1", "task");
        add("cosmetics/cat_stack", "Cat Person", "Wear a cat, hold a cat, and hold another cat in your off hand.", "fake:cosmetic-calico-cat-pet-hat", "cosmetics/buy_1", "challenge");
        add("cosmetics/sombrero", "Reasonably Sized", "Buy the Sombrero.", "fake:cosmetic-sombrero", "cosmetics/buy_1", "task");
        add("cosmetics/sombreron", "Compensating for Something", "Buy the Sombreron.", "fake:cosmetic-sombreron", "cosmetics/sombrero", "goal");
        add("cosmetics/sombreronn", "Personal Weather System", "Buy the largest Sombreronn.", "fake:cosmetic-sombreronn", "cosmetics/sombreron", "challenge");
        add("cosmetics/solar_eclipse", "Solar Eclipse", "Wear Giant's Boots and the largest Sombreronn at the same time.", "fake:cosmetic-sombreronn", "cosmetics/sombreronn", "goal");
        add("cosmetics/crown_techno", "Technically Royal", "Buy the Techno Crown.", "fake:cosmetic-crown-techno", "cosmetics/buy_1", "goal");
        add("cosmetics/crown_royal", "Royalty", "Buy the Royal Crown.", "fake:cosmetic-crown-royal", "cosmetics/crown_techno", "challenge");
        add("cosmetics/crown_ornate", "Heavy Is the Head", "Buy the Ornate Crown.", "fake:cosmetic-crown-ornate", "cosmetics/crown_royal", "challenge");
        add("cosmetics/buy_all", "Buy the Shop", "Buy as many cosmetics as the full catalogue contains.", "minecraft:emerald_block", "cosmetics/buy_100", "challenge");
        add("cosmetics/buy_common", "Common Purchase", "Buy a common cosmetic.", "fake:cosmetic-beret", "cosmetics/buy_1", "task");
        add("cosmetics/buy_uncommon", "Uncommon Purchase", "Buy an uncommon cosmetic.", "fake:cosmetic-academic-hat", "cosmetics/buy_1", "task");
        add("cosmetics/buy_rare", "Rare Purchase", "Buy a rare cosmetic.", "fake:cosmetic-calico-cat-pet-hat", "cosmetics/buy_1", "goal");
        add("cosmetics/buy_epic", "Epic Purchase", "Buy an epic cosmetic.", "fake:cosmetic-posh-squid", "cosmetics/buy_1", "goal");
        add("cosmetics/buy_legendary", "Legendary Purchase", "Buy a legendary cosmetic.", "fake:cosmetic-crown-royal", "cosmetics/buy_1", "challenge");
        add("cosmetics/buy_mythical", "Mythical Purchase", "Buy a mythical cosmetic.", "fake:cosmetic-crown-techno", "cosmetics/buy_1", "challenge");
        add("charms/apply_boost", "Armoured and Charmed", "Apply Charm Boost to a piece of armour.", "minecraft:diamond_chestplate", "charms/get_1", "goal");
        add("charms/upgrade_once", "Potential Unlocked", "Upgrade a charm once.", "minecraft:anvil", "charms/get_1", "task");
        add("charms/upgrade_max", "Maximum Charm", "Upgrade one charm to its maximum level.", "fake:charm-heart", "charms/upgrade_3", "challenge");
        add("charms/upgrade_all_max", "Perfectly Charming", "Upgrade every charm to its maximum level.", "minecraft:beacon", "charms/upgrade_max", "challenge");
        add("charms/get_all", "Every Charm", "Hold every charm at once.", "fake:charm-heart", "charms/get_20", "challenge");
        add("utility/wallet", "Cash Container", "Get a wallet.", "fake:charm-wallet", "root", "task");
        add("utility/soulbound", "Till Death Doesn't Part Us", "Apply Soulbound to an item. It will no longer leave you, even after death.", "minecraft:recovery_compass", "root", "goal");
        add("utility/soulbound_recovery_compass", "Well, This Is Handy", "Apply Soulbound to a Recovery Compass.", "minecraft:recovery_compass", "utility/soulbound", "goal");
        add("utility/use_potion", "Experimental Medicine", "Use one custom potion.", "fake:charm-potion-returning", "root", "task");
        add("utility/all_potions", "MMU Mixologist", "Use every custom potion.", "fake:charm-potion-resonance", "utility/use_potion", "challenge");
        add("utility/use_staff", "Staff Meeting", "Use a utility staff.", "fake:charm-crafting-staff", "root", "task");
        add("utility/all_staves", "Utility Wizard", "Use every utility staff.", "fake:charm-umbrella-staff", "utility/use_staff", "goal");
        add("utility/staff_crafting", "Pocket Workbench", "Use the Staff of Crafting.", "fake:charm-crafting-staff", "utility/use_staff", "task");
        add("utility/staff_ender_chest", "Pocket Storage", "Use the Staff of Soulbound Storage.", "fake:charm-ender-chest-staff", "utility/use_staff", "task");
        add("utility/staff_brolly", "Practically Perfect", "Use the Staff of Brolly to slow your fall.", "fake:charm-umbrella-staff", "utility/use_staff", "task");
        add("utility/wrench", "Be a Mensch", "Use a wrench.", "fake:charm-wrench", "root", "task");
        add("utility/sculk_phial_extract", "Experience on Tap", "Extract XP from a Sculk Phial into a bottle.", "fake:charm-sculk-phial", "root", "task");
        add("utility/sculk_phial_full", "Bottled Wisdom", "Fill a Sculk Phial completely.", "fake:charm-sculk-phial-full", "utility/sculk_phial_extract", "goal");
        add("utility/invisible_frame", "Now You See It", "Use an Invisi-Carrot on an item frame.", "fake:charm-invisi-carrot", "root", "goal");
        add("utility/golden_paste", "Premium Nutrition", "Drink Golden Nutritional Paste.", "fake:golden-nutritional-paste", "root", "task");
        add("utility/slime_detector", "Slime Surveyor", "Use a slime detector.", "minecraft:slime_ball", "root", "task");
        add("utility/slime_chunk", "Standing on Slime", "Use a slime detector inside a slime chunk.", "minecraft:slime_block", "utility/slime_detector", "goal");
        add("utility/no_slime_bars", "Nothing to Report", "Use a slime detector while no bars appear.", "minecraft:barrier", "utility/slime_detector", "goal");
        add("utility/duplicate_book", "Copy Paste", "Duplicate an enchanted book by placing it on a librarian's lectern.", "minecraft:enchanted_book", "root", "goal");
        add("utility/sniffer_slayer", "The Sniffer Slayer", "Name a sword “the sniffer slayer” in an anvil.", "minecraft:iron_sword", "root", "challenge");
        add("utility/full_speed_minecart", "Twenty Metres Per Second", "Travel at full speed in a minecart.", "minecraft:minecart", "root", "challenge");
        add("utility/max_reach_beacon", "Long-Distance Service", "Create a beacon with 200 blocks of reach.", "minecraft:beacon", "root", "challenge");
        add("utility/decoblock", "Interior Designer", "Place a deco block.", "minecraft:flower_pot", "root", "task");
        add("utility/particle_trail", "Leave a Mark", "Apply a particle trail to a tool.", "minecraft:firework_star", "root", "goal");
        add("glider/craft", "Learning to Soar", "Make a glider.", "fake:charm-glider", "root", "task");
        add("glider/fly", "Cheap Flight", "Fly with a glider.", "fake:charm-glider", "glider/craft", "goal");
        add("glider/updraft", "Hot Air Rises", "Glide over a heat source.", "minecraft:campfire", "glider/fly", "goal");
        add("glider/firework_fail", "Nice Try", "Try and fail to boost a glider with a firework.", "minecraft:firework_rocket", "glider/fly", "goal");
        add("glider/amethyst_ring", "Thread the Needle", "Glide through an amethyst ring.", "minecraft:amethyst_block", "glider/fly", "challenge");
        add("enderite/scrap", "Into the Void", "Obtain Enderite Scrap.", "fake:enderite-scrap", "root", "task");
        add("enderite/scrap_4", "Enough to Forge Ahead", "Hold four Enderite Scraps, enough for an ingot.", "fake:enderite-scrap:glint", "enderite/scrap", "goal");
        add("enderite/ingot", "Ender Upgrade", "Obtain an Enderite Ingot.", "fake:enderite-ingot", "enderite/scrap", "goal");
        add("enderite/template", "Serious Dedication, Again", "Obtain an Enderite Upgrade template.", "fake:enderite-upgrade-smithing-template", "enderite/ingot", "goal");
        add("enderite/armor", "Cover Me in the End", "Wear a full suit of Enderite armour.", "enderite:netherite_chestplate", "enderite/template", "challenge");
        add("enderite/hoe", "Serious Dedication: Ender Edition", "Obtain an Enderite hoe.", "enderite:netherite_hoe", "enderite/ingot", "challenge");
        add("social/pay_to_win", "Pay to Win", "Become a member. Thank you for supporting the society!", "minecraft:diamond", "root", "goal");
        add("social/add_claim_member", "Good Fences, Good Friends", "Add another player to one of your claims.", "minecraft:player_head", "social/claim_1", "goal");
        add("social/statistic_first", "Number One", "Become first in any statistic.", "minecraft:gold_block", "root", "challenge");
        add("social/committee_any", "Sunday Committee Meeting", "Kill any committee member on a Sunday (British time).", "enderite:netherite_sword", "root", "goal");
        for (CommitteeMembers.Member member : CommitteeMembers.ALL) {
            add(member.advancementPath(), "Sunday Visit: " + member.name(),
                    "Kill " + member.name() + " (" + member.username() + "), " + member.role() + ", on a Sunday.",
                    "head:" + member.username(), "social/committee_any", "goal");
        }
        add("social/committee_all", "The Whole Committee", "Kill every committee member on a Sunday (British time).",
                "enderite:netherite_sword:glint", CommitteeMembers.ALL.getLast().advancementPath(), "challenge");
        add("social/gift_code", "The Secret Word", "Receive Dabloons from a gift code.", "minecraft:paper", "root", "task");
        add("social/referral", "Bring a Friend", "Invite someone who joins the society server.", "minecraft:player_head", "root", "task");
        add("social/daily_1", "A Job Well Done", "Complete a daily task.", "minecraft:clock", "root", "task");
        add("social/full_profile", "Known Quantity", "Fill in every field on your player profile.", "minecraft:name_tag", "root", "goal");
        add("knowledge/read_all", "Omniscient", "Read every knowledge page.", "fake:charm-knowledge-book", "knowledge/read_20", "challenge");
        add("social/join_insane_hour", "The Ungodly Hour", "Join at an ungodly hour (between 2 a.m. and 7 a.m. British time).", "minecraft:clock", "root", "task");
        add("social/unreasonable_hours", "What Is Sleep?", "Join during every one-hour window from 2 a.m. to 7 a.m. British time.", "minecraft:clock:glint", "social/join_6", "challenge");
        add("food/sushi", "Freshly Rolled", "Eat Sushi (craft it with any fish and kelp).", "fake:sushi", "fishing/first_catch", "task");
        add("food/beer", "Cheers!", "Drink beer.", "fake:beer", "root", "task");
        add("food/beer_again", "Hair of the Dog", "Drink beer while already under its effects.", "fake:beer", "food/beer", "goal");
        chain("charms/get", new int[]{1, 3, 5, 10, 15, 20}, "Charm Collector", "Hold %s distinct charms at once.", "fake:charm-heart", "root");
        chain("charms/upgrade", new int[]{3, 5, 10, 15, 20}, "Charm Tinkerer", "Upgrade charms %s times.", "fake:charm-heart", "charms/upgrade_once");
        int[] cosmeticMilestones = {1, 5, 10, 20, 30, 40, 50, 75, 100};
        String[] cosmeticMilestoneIcons = {"cosmetic-clown-nose", "cosmetic-beret", "cosmetic-sun-hat",
                "cosmetic-academic-hat", "cosmetic-propeller", "cosmetic-posh-squid", "cosmetic-crown-royal",
                "cosmetic-halo-hat", "cosmetic-crown-techno"};
        String unlockParent = "root";
        String buyParent = "cosmetics/unlock_1";
        for (int i = 0; i < cosmeticMilestones.length; i++) {
            int count = cosmeticMilestones[i];
            String icon = "fake:" + cosmeticMilestoneIcons[i];
            add("cosmetics/unlock_" + count, "Wardrobe " + count,
                    "Unlock " + count + " distinct cosmetics in the shop.", icon, unlockParent,
                    count >= 100 ? "challenge" : count >= 10 ? "goal" : "task");
            add("cosmetics/buy_" + count, "Retail Therapy " + count,
                    "Buy " + count + " cosmetics.", icon, buyParent,
                    count >= 100 ? "challenge" : count >= 10 ? "goal" : "task");
            unlockParent = "cosmetics/unlock_" + count;
            buyParent = "cosmetics/buy_" + count;
        }
        add("cosmetics/unlock_all", "Complete Wardrobe", "Unlock every cosmetic in the shop.", "fake:cosmetic-crown-techno", "cosmetics/unlock_100", "challenge");
        String[] rarities = {"common", "uncommon", "rare", "epic", "legendary", "mythical"};
        String[] rarityIcons = {"cosmetic-beret", "cosmetic-academic-hat", "cosmetic-calico-cat-pet-hat", "cosmetic-posh-squid", "cosmetic-crown-royal", "cosmetic-crown-techno"};
        for (int i = 0; i < rarities.length; i++) {
            String rarity = rarities[i];
            add("cosmetics/unlock_" + rarity, Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1) + " Style",
                    "Unlock a " + rarity + " cosmetic in the shop.", "fake:" + rarityIcons[i],
                    "cosmetics/unlock_1", i >= 4 ? "challenge" : "task");
        }
        add("knowledge/read_1", "Well Read 1", "Read a knowledge page.", "fake:charm-knowledge-book", "root", "task");
        chain("knowledge/read", new int[]{3, 5, 10, 15, 20}, "Well Read", "Read %s knowledge pages.", "fake:charm-knowledge-book", "knowledge/read_1");
        chain("social/claim", new int[]{1, 3, 5, 7, 10, 12, 15}, "Landowner", "Reach %s claimed chunks.", "minecraft:map", "root");
        chain("social/streak", new int[]{1, 2, 3, 5, 7, 10, 14, 30, 50, 75, 100}, "Login Streak", "Reach a %s-day login streak.", "minecraft:fire_charge", "root");
        chain("social/full_dailies", new int[]{1, 3, 5, 7, 10, 14, 30, 50, 75, 100}, "Daily Devotion", "Fully complete dailies on %s days.", "minecraft:clock", "social/daily_1");
        int[] snifferMilestones = {1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 10000};
        chain("sniffers/bred", snifferMilestones, "Sniffer Population", "Breed %s sniffers.", "minecraft:sniffer_egg", "root");
        reparent("sniffers/bred_10000", "sniffers/bred_500");
        for (int count : snifferMilestones) {
            add("sniffers/control_" + count, "Sniffer Population Control " + count,
                    "Kill " + count + (count == 1 ? " sniffer." : " sniffers."), "minecraft:iron_sword",
                    "sniffers/bred_" + count, count >= 100 ? "challenge" : count == 1 ? "task" : "goal");
        }
        chain("utility/jokes", new int[]{1, 3, 5}, "Critic", "Review Joke Books: %s completed.", "fake:charm-joke-book", "root");
        int[] coinValues = {1, 5, 10, 50, 100, 500, 1000, 5000, 10000};
        String coinParent = "utility/wallet";
        for (int value : coinValues) {
            String path = "coins/hold_" + value;
            add(path, FakeItems.requireFakeItem("coin-" + value).tooltip().getFirst().getString(),
                    "Hold a " + value + "-Dabloon coin.", "fake:coin-" + value,
                    coinParent, value >= 100000 ? "challenge" : "task");
            coinParent = path;
        }
        chain("money/balance", new int[]{100, 500, 1000, 5000, 10000, 100000, 1000000},
                "Liquid Assets", "Hold %s Dabloons at once.", "fake:charm-wallet", "utility/wallet");
        String[] backpacks = {"leather", "ingot", "magic", "bejeweled", "withered", "endless"};
        String backpackParent = "root";
        for (int i = 0; i < backpacks.length; i++) {
            String tier = backpacks[i];
            add("backpacks/" + tier, Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + " Backpack",
                    "Craft a " + tier + " backpack.", "fake:charm-" + tier + "-backpack", backpackParent,
                    i >= 4 ? "goal" : "task");
            backpackParent = "backpacks/" + tier;
        }
        String hourParent = "social/join_insane_hour";
        for (int hour = 2; hour <= 6; hour++) {
            add("social/join_" + hour, hour + " a.m. Club",
                    "Join between " + hour + " a.m. and " + (hour + 1) + " a.m. British time.",
                    "minecraft:clock", hourParent, "goal");
            hourParent = "social/join_" + hour;
        }
        for (String potion : new String[]{"returning", "displacement", "insomnia", "resonance"}) {
            add("potions/" + potion, "Potion of " + Character.toUpperCase(potion.charAt(0)) + potion.substring(1),
                    "Use the Potion of " + Character.toUpperCase(potion.charAt(0)) + potion.substring(1) + ".",
                    "fake:charm-potion-" + potion, "utility/use_potion", "task");
        }
        for (int level = 1; level <= 5; level++) {
            add("fishing/lucky_charm_" + level, "Lucky Charm " + level,
                    "Upgrade the Lucky Charm to level " + level + ".", "fake:charm-lucky-charm",
                    level == 1 ? "fishing/luck_3" : "fishing/lucky_charm_" + (level - 1), level == 5 ? "challenge" : "task");
        }
        add("fishing/special_condition", "There's a Time and a Plaice", "Catch a fish that needs a special condition to spawn.",
                "minecraft:compass", "fishing/first_catch", "goal");
        String conditionParent = "fishing/special_condition";
        for (FishSpawnTag tag : new FishSpawnTag[]{FishSpawnTag.RIVER, FishSpawnTag.OCEAN, FishSpawnTag.DAY,
                FishSpawnTag.NIGHT, FishSpawnTag.DEEP, FishSpawnTag.RAINY, FishSpawnTag.THUNDERSTORM,
                FishSpawnTag.FULLMOON, FishSpawnTag.NEWMOON}) {
            String name = tag.name().toLowerCase(java.util.Locale.ROOT);
            add("fishing/condition/" + name, conditionTitle(tag),
                    "Catch a fish that requires the " + name + " condition.", "minecraft:compass",
                    conditionParent, "goal");
            conditionParent = "fishing/condition/" + name;
        }
        line("fishing/common", "fishing/acoustic_bass", "fishing/goldfish", "fishing/vampire_carp",
                "fishing/thundering_bass", "fishing/swordfish");
        line("fishing/uncommon", "fishing/rainbow_trout", "fishing/galaxy_starfish", "fishing/thunderfin", "fishing/skyfish");
        line("fishing/rare", "fishing/baguette_fish", "fishing/nebula_swordfish", "fishing/witchfish");
        line("fishing/epic", "fishing/freddy_fish", "fishing/charged_thunderfin");
        FakeItems.FISH.entrySet().stream().filter(entry ->
                entry.getValue().personality().rarity().ordinal() >= FishRarity.LEGENDARY.ordinal()
                        && !entry.getKey().id().equals("fish-matrix_fish")
                        && !entry.getKey().id().equals("fish-spook_fish")
        ).sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(item -> item.id()))).forEach(entry -> {
            String rarity = entry.getValue().personality().rarity().name().toLowerCase(java.util.Locale.ROOT);
            add("fishing/species/" + rarity + "/" + entry.getKey().id().substring(5), entry.getKey().title(),
                    "Catch a " + entry.getKey().title() + ".", "fake:" + entry.getKey().id(),
                    "fishing/" + rarity, "challenge");
        });
        line("fishing/legendary", "fishing/matrix_fish", "fishing/species/legendary/divine_catfish",
                "fishing/species/legendary/divine_jellyfish", "fishing/species/legendary/golden_swordfish",
                "fishing/species/legendary/nullfin");
        line("fishing/legendary", "fishing/species/legendary/pandafish", "fishing/species/legendary/windfish");
        line("fishing/mythical", "fishing/spook_fish", "fishing/species/mythical/coelacanth",
                "fishing/species/mythical/golden_fish");
        FakeItems.ALL.stream().filter(item -> item.shopPurchasable()
                        && item.getFeature(EquippableCosmeticItemFeature.class) != null)
                .sorted(java.util.Comparator.comparing(item -> item.id())).forEach(item -> {
                    String title = cosmeticTitle(item.id());
                    if (title != null) {
                        add("cosmetics/item/" + item.id().substring(9), title,
                                "Buy the " + item.title() + ".", "fake:" + item.id(), "cosmetics/buy_1", "task");
                    }
                });
        line("cosmetics/buy_1", "cosmetics/amogus", "cosmetics/villager_nose", "cosmetics/fletcher_hat",
                "cosmetics/good_boy", "cosmetics/academic_hat", "cosmetics/straw_hat_farmer",
                "cosmetics/straw_hat_sun", "cosmetics/posh_squid");
        line("cosmetics/buy_1", "cosmetics/retro_helmet", "cosmetics/trans_bee_hood", "cosmetics/sombrero",
                "cosmetics/sombreron", "cosmetics/sombreronn", "cosmetics/crown_techno",
                "cosmetics/crown_royal", "cosmetics/crown_ornate");
        String cosmeticParent = "cosmetics/buy_1";
        int cosmeticCount = 0;
        for (String path : List.copyOf(definitions.keySet())) {
            if (!path.startsWith("cosmetics/item/")) continue;
            reparent(path, cosmeticParent);
            cosmeticParent = path;
            if (++cosmeticCount % 8 == 0) cosmeticParent = "cosmetics/buy_1";
        }
    }

    private static String cosmeticTitle(String id) {
        return switch (id) {
            case "cosmetic-arrow" -> "I Think You've Got Something There";
            case "cosmetic-ice-cream" -> "Brain Freeze";
            case "cosmetic-shulker" -> "Thinking Inside the Box";
            case "cosmetic-slime-head" -> "Sticky Situation";
            case "cosmetic-halo-hat" -> "Holier Than Thou";
            case "cosmetic-devil-horns" -> "Little Devil";
            case "cosmetic-unicorn-horn" -> "One of a Kind";
            case "cosmetic-spider" -> "Eight-Legged Fashion";
            case "cosmetic-cod" -> "Cod Save the King";
            case "cosmetic-salmon" -> "Salmon Chanted Evening";
            case "cosmetic-chick-tower" -> "Pecking Order";
            case "cosmetic-frog-small" -> "Croak Couture";
            case "cosmetic-frying-pan" -> "Out of the Frying Pan";
            case "cosmetic-aviator-hat" -> "Head in the Clouds";
            case "cosmetic-propeller" -> "Ready for Takeoff";
            case "cosmetic-pirate" -> "Arr You Serious?";
            case "cosmetic-hard-hat" -> "Safety First";
            case "cosmetic-party-hat" -> "Party on Your Head";
            case "cosmetic-cowboy-hat" -> "Howdy, Partner";
            case "cosmetic-bowler-hat" -> "Mind the Gap";
            case "cosmetic-ushanka" -> "Keep Your Ears Warm";
            case "cosmetic-clown-nose" -> "Honk Honk";
            case "cosmetic-headphones" -> "Do Not Disturb";
            case "cosmetic-sleeping-mobs" -> "Let Sleeping Mobs Lie";
            case "cosmetic-warden-antlers" -> "Can You Hear Me Now?";
            case "cosmetic-obamium-pyramid" -> "Yes We Can";
            default -> null;
        };
    }

    private static String conditionTitle(FishSpawnTag tag) {
        return switch (tag) {
            case WARM -> "Warm Waters";
            case COLD -> "Cold Comfort";
            case RIVER -> "Go with the Flow";
            case OCEAN -> "Open Water";
            case DAY -> "Daylight Saving";
            case NIGHT -> "Midnight Snack";
            case DEEP -> "Under Pressure";
            case HIGH -> "High Tide";
            case RAINY -> "Rain Check";
            case THUNDERSTORM -> "Thunderstruck";
            case SNOWY -> "Snow Problem";
            case WAXING -> "Wax On";
            case WANING -> "Wax Off";
            case FULLMOON -> "Full Moon Rising";
            case NEWMOON -> "Dark Side of the Moon";
        };
    }

    private record Definition(String parent, String title, String description, String icon, String frame) {}
}
