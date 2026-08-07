package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonObject;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class DailyTaskRegistry {
    public static final boolean NETHER_ENABLED = false;
    public static final boolean END_ENABLED = false;

    private static final Map<Class<?>, Integer> FAMILY_WEIGHTS = Map.ofEntries(
            Map.entry(ItemSubmissionTask.class, 12),
            Map.entry(EatItemTask.class, 5),
            Map.entry(EnchantAtTableTask.class, 6),
            Map.entry(EnchantWithEnchantmentTask.class, 2),
            Map.entry(KillEntityTask.class, 4),
            Map.entry(ReceiveEffectTask.class, 4),
            Map.entry(GainLevelsTask.class, 3),
            Map.entry(BreedEntityTask.class, 4),
            Map.entry(FeedEntityTask.class, 5),
            Map.entry(VillagerTradeTask.class, 7),
            Map.entry(BrewPotionTask.class, 4),
            Map.entry(BrushBlockTask.class, 3),
            Map.entry(FishTask.class, 6),
            Map.entry(BreakBlockTask.class, 5),
            Map.entry(SimpleEventTask.class, 7),
            Map.entry(UseItemTask.class, 4),
            Map.entry(UseCharmTask.class, 3),
            Map.entry(RideDistanceTask.class, 5),
            Map.entry(KillWithItemTask.class, 4),
            Map.entry(HitPlayerWithProjectileTask.class, 4),
            Map.entry(PlantCropTask.class, 5),
            Map.entry(CreateGolemTask.class, 3),
            Map.entry(TakeDamageTask.class, 5),
            Map.entry(PlayNoteBlockTask.class, 3),
            Map.entry(UseBlockTask.class, 2),
            Map.entry(CraftItemTask.class, 6),
            Map.entry(PlayTimeTask.class, 5),
            Map.entry(CureZombieVillagerTask.class, 4)
    );

    private static final List<Option> TASKS = List.of(
            // Item submissions
            option(16, false, false, new ItemSubmissionTask(Items.POISONOUS_POTATO, "Poisonous Potatoes", "🥔", 4, 10, 1.5D)),
            option(16, false, false, new ItemSubmissionTask(Items.DEAD_BUSH, "Dead Bushes", "🌵", 4, 12, 1.25D)),
            option(16, false, false, new ItemSubmissionTask(Items.TINTED_GLASS, "Tinted Glass", "🪟", 4, 10, 1.5D)),
            option(1, false, false, flower(Items.SUNFLOWER, "Sunflowers", "🌻")),
            option(16, false, false, new ItemSubmissionTask(Items.GLOW_INK_SAC, "Glow Ink Sacs", "🦑", 6, 16, 1.0D)),
            option(16, false, false, new ItemSubmissionTask(Items.HONEYCOMB, "Honeycomb", "🍯", 8, 20, 0.8D)),
            option(12, true, false, new ItemSubmissionTask(Items.BLAZE_ROD, "Blaze Rods", "🔥", 4, 10, 2.0D)),
            option(16, false, false, new ItemSubmissionTask(Items.CANDLE, "Candles", "Submit {count} candles. Keep a few and make somewhere cosy.", "🕯️", 4, 10, 1.5D)),
            option(12, false, false, new ItemSubmissionTask(Items.DYED_CANDLE.blue(), "Blue Candles", "Submit {count} blue candles. Blue light is still light.", "🔵", 3, 8, 2.0D)),
            option(8, false, false, new ItemSubmissionTask(Items.TURTLE_EGG, "Turtle Eggs", "Submit one turtle egg without letting anything stomp on it.", "🐢", 1, 1, 15.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.SPYGLASS, "Spyglasses", "Submit one spyglass. The horizon can wait.", "🔭", 1, 1, 12.0D)),
            option(16, false, false, new ItemSubmissionTask(Items.AMETHYST_SHARD, "Amethyst Shards", "Submit {count} amethyst shards. Geodes grow more.", "💎", 12, 32, 0.5D)),
            option(8, false, false, new ItemSubmissionTask(Items.ECHO_SHARD, "Echo Shards", "Submit {count} echo shards from the deep dark.", "📡", 1, 3, 8.0D)),
            option(1, false, false, sherd(Items.ANGLER_POTTERY_SHERD, "Angler Sherd", "🏺")),
            option(1, false, false, disc(Items.MUSIC_DISC_13, "Music Disc 13", "💿", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_CAT, "Music Disc Cat", "🐈", 20.0D)),
            option(1, false, false, trim(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "Sentry Trim", "🏴", 18.0D)),
            option(1, false, false, trim(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "Silence Trim", "🤫", 35.0D)),
            option(1, false, false, sherd(Items.ARCHER_POTTERY_SHERD, "Archer Sherd", "🏹")),
            option(1, false, false, sherd(Items.ARMS_UP_POTTERY_SHERD, "Arms Up Sherd", "🙌")),
            option(1, false, false, sherd(Items.BLADE_POTTERY_SHERD, "Blade Sherd", "🗡️")),
            option(1, false, false, sherd(Items.BREWER_POTTERY_SHERD, "Brewer Sherd", "🧪")),
            option(1, false, false, sherd(Items.BURN_POTTERY_SHERD, "Burn Sherd", "🔥")),
            option(1, false, false, sherd(Items.DANGER_POTTERY_SHERD, "Danger Sherd", "⚠️")),
            option(1, false, false, sherd(Items.EXPLORER_POTTERY_SHERD, "Explorer Sherd", "🧭")),
            option(1, false, false, sherd(Items.FLOW_POTTERY_SHERD, "Flow Sherd", "🌊")),
            option(1, false, false, sherd(Items.FRIEND_POTTERY_SHERD, "Friend Sherd", "🤝")),
            option(1, false, false, sherd(Items.GUSTER_POTTERY_SHERD, "Guster Sherd", "🌬️")),
            option(1, false, false, sherd(Items.HEART_POTTERY_SHERD, "Heart Sherd", "❤️")),
            option(1, false, false, sherd(Items.HEARTBREAK_POTTERY_SHERD, "Heartbreak Sherd", "💔")),
            option(1, false, false, sherd(Items.HOWL_POTTERY_SHERD, "Howl Sherd", "🐺")),
            option(1, false, false, sherd(Items.MINER_POTTERY_SHERD, "Miner Sherd", "⛏️")),
            option(1, false, false, sherd(Items.MOURNER_POTTERY_SHERD, "Mourner Sherd", "😢")),
            option(1, false, false, sherd(Items.PLENTY_POTTERY_SHERD, "Plenty Sherd", "🌾")),
            option(1, false, false, sherd(Items.PRIZE_POTTERY_SHERD, "Prize Sherd", "🏆")),
            option(1, false, false, sherd(Items.SCRAPE_POTTERY_SHERD, "Scrape Sherd", "🖌️")),
            option(1, false, false, sherd(Items.SHEAF_POTTERY_SHERD, "Sheaf Sherd", "🌾")),
            option(1, false, false, sherd(Items.SHELTER_POTTERY_SHERD, "Shelter Sherd", "🏠")),
            option(1, false, false, sherd(Items.SKULL_POTTERY_SHERD, "Skull Sherd", "💀")),
            option(1, false, false, sherd(Items.SNORT_POTTERY_SHERD, "Snort Sherd", "🐽")),
            option(1, false, false, disc(Items.MUSIC_DISC_BLOCKS, "Music Disc Blocks", "🧱", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_BOUNCE, "Music Disc Bounce", "🏀", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_CHIRP, "Music Disc Chirp", "🐦", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_CREATOR, "Music Disc Creator", "🛠️", 26.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_CREATOR_MUSIC_BOX, "Creator Music Box", "🎶", 26.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_FAR, "Music Disc Far", "🏞️", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_LAVA_CHICKEN, "Lava Chicken Disc", "🐔", 24.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_MALL, "Music Disc Mall", "🛍️", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_MELLOHI, "Music Disc Mellohi", "🎼", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_STAL, "Music Disc Stal", "🪨", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_STRAD, "Music Disc Strad", "🎻", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_WARD, "Music Disc Ward", "🛡️", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_11, "Music Disc 11", "🔢", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_WAIT, "Music Disc Wait", "⏳", 20.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_OTHERSIDE, "Music Disc Otherside", "🚪", 26.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_RELIC, "Music Disc Relic", "🏺", 28.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_5, "Music Disc 5", "5️⃣", 30.0D)),
            option(1, true, false, disc(Items.MUSIC_DISC_PIGSTEP, "Music Disc Pigstep", "🐽", 32.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_PRECIPICE, "Music Disc Precipice", "⛰️", 28.0D)),
            option(1, false, false, disc(Items.MUSIC_DISC_TEARS, "Music Disc Tears", "😭", 26.0D)),
            option(1, false, false, trim(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, "Dune Trim", "🏜️", 18.0D)),
            option(1, false, false, trim(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "Coast Trim", "🌊", 18.0D)),
            option(1, false, false, trim(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, "Wild Trim", "🌿", 20.0D)),
            option(1, false, false, trim(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, "Ward Trim", "📡", 26.0D)),
            option(1, false, false, trim(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, "Eye Trim", "👁️", 22.0D)),
            option(1, false, false, trim(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, "Vex Trim", "🪽", 26.0D)),
            option(1, false, false, trim(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, "Tide Trim", "🔱", 26.0D)),
            option(1, true, false, trim(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, "Snout Trim", "🐽", 24.0D)),
            option(1, true, false, trim(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, "Rib Trim", "🦴", 24.0D)),
            option(1, false, true, trim(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "Spire Trim", "🏙️", 28.0D)),
            option(1, false, false, trim(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, "Wayfinder Trim", "🧭", 28.0D)),
            option(1, false, false, trim(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, "Shaper Trim", "🏺", 28.0D)),
            option(1, false, false, trim(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, "Raiser Trim", "🙌", 28.0D)),
            option(1, false, false, trim(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, "Host Trim", "🏠", 28.0D)),
            option(1, false, false, trim(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, "Flow Trim", "🌬️", 24.0D)),
            option(1, false, false, trim(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, "Bolt Trim", "⚡", 24.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.ZOMBIE_HEAD, "Zombie Heads", "Submit {count} zombie heads. Around here, every tenth zombie has one to spare.", "🧟", 1, 3, 8.0D)),
            option(6, false, false, new ItemSubmissionTask(Items.CREEPER_HEAD, "Creeper Heads", "Submit one creeper head. No charged creeper paperwork is required here.", "💥", 1, 1, 14.0D)),
            option(6, true, false, new ItemSubmissionTask(Items.PIGLIN_HEAD, "Piglin Heads", "Submit one piglin head. The Nether has lost property too.", "🐽", 1, 1, 14.0D)),
            option(6, true, false, new ItemSubmissionTask(Items.WITHER_SKELETON_SKULL, "Wither Skeleton Skulls", "Submit one wither skeleton skull. Keep two more if you have plans.", "💀", 1, 1, 20.0D)),
            option(4, false, true, new ItemSubmissionTask(Items.DRAGON_HEAD, "Dragon Heads", "Submit a dragon head. It is difficult to decorate with two anyway.", "🐉", 1, 1, 25.0D)),
            option(6, true, false, new ItemSubmissionTask(Items.OCHRE_FROGLIGHT, "Ochre Froglights", "Submit {count} ochre froglights. Keep the next batch for a warm ceiling.", "🟡", 1, 3, 8.0D)),
            option(6, true, false, new ItemSubmissionTask(Items.VERDANT_FROGLIGHT, "Verdant Froglights", "Submit {count} verdant froglights. They make excellent hidden lighting.", "🟢", 1, 3, 8.0D)),
            option(6, true, false, new ItemSubmissionTask(Items.PEARLESCENT_FROGLIGHT, "Pearlescent Froglights", "Submit {count} pearlescent froglights. Try the next ones in a floor pattern.", "🟣", 1, 3, 8.0D)),

            // Flowers have low variant weights so their variety does not crowd out the rest of the family.
            option(1, false, false, flower(Items.DANDELION, "Dandelions", "🌼")),
            option(1, false, false, flower(Items.POPPY, "Poppies", "🌹")),
            option(1, false, false, flower(Items.BLUE_ORCHID, "Blue Orchids", "🪻")),
            option(1, false, false, flower(Items.ALLIUM, "Alliums", "🟣")),
            option(1, false, false, flower(Items.AZURE_BLUET, "Azure Bluets", "🌼")),
            option(1, false, false, flower(Items.RED_TULIP, "Red Tulips", "🌷")),
            option(1, false, false, flower(Items.ORANGE_TULIP, "Orange Tulips", "🌷")),
            option(1, false, false, flower(Items.WHITE_TULIP, "White Tulips", "🌷")),
            option(1, false, false, flower(Items.PINK_TULIP, "Pink Tulips", "🌷")),
            option(1, false, false, flower(Items.OXEYE_DAISY, "Oxeye Daisies", "🌼")),
            option(1, false, false, flower(Items.CORNFLOWER, "Cornflowers", "🪻")),
            option(1, false, false, flower(Items.LILY_OF_THE_VALLEY, "Lilies of the Valley", "🤍")),
            option(1, false, false, flower(Items.LILAC, "Lilacs", "🪻")),
            option(1, false, false, flower(Items.ROSE_BUSH, "Rose Bushes", "🌹")),
            option(1, false, false, flower(Items.PEONY, "Peonies", "🌸")),
            option(1, false, false, flower(Items.WILDFLOWERS, "Wildflowers", "💐")),
            option(1, false, false, flower(Items.PINK_PETALS, "Pink Petals", "🌸")),
            option(1, false, false, flower(Items.CACTUS_FLOWER, "Cactus Flowers", "🌵")),
            option(1, false, false, new ItemSubmissionTask(Items.SPORE_BLOSSOM, "Spore Blossoms", "Submit {count} spore blossoms. Give the next lush cave a ceiling garden.", "🌺", 2, 5, 3.0D)),
            option(1, true, false, new ItemSubmissionTask(Items.WITHER_ROSE, "Wither Roses", "Submit one wither rose. This bouquet bites back.", "🥀", 1, 1, 18.0D)),
            option(1, false, true, new ItemSubmissionTask(Items.CHORUS_FLOWER, "Chorus Flowers", "Submit {count} chorus flowers. They have travelled far enough.", "🟪", 2, 5, 3.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.TORCHFLOWER, "Torchflowers", "Submit {count} torchflowers. Let the next ancient seed brighten your garden.", "🏵️", 1, 3, 6.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.PITCHER_PLANT, "Pitcher Plants", "Submit {count} pitcher plants. The sniffer approves of the landscaping.", "🪻", 1, 3, 6.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.OPEN_EYEBLOSSOM, "Open Eyeblossoms", "Submit {count} open eyeblossoms while they are watching.", "👁️", 3, 8, 2.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.CLOSED_EYEBLOSSOM, "Closed Eyeblossoms", "Submit {count} closed eyeblossoms. Do not wake them.", "😴", 3, 8, 2.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.GOLDEN_DANDELION, "Golden Dandelions", "Submit one golden dandelion. Ordinary yellow was not enough.", "🌟", 1, 1, 15.0D)),

            // Unusual building materials and natural finds.
            option(10, false, false, new ItemSubmissionTask(Items.AMETHYST_CLUSTER, "Amethyst Clusters", "Submit {count} amethyst clusters. Silk Touch keeps the sparkle intact.", "🔮", 1, 2, 7.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.ARMADILLO_SCUTE, "Armadillo Scutes", "🛡️", 4, 10, 1.5D)),
            option(8, false, false, new ItemSubmissionTask(Items.AXOLOTL_BUCKET, "Buckets of Axolotl", "Submit one bucket of axolotl. It is a passenger, not cargo.", "🪣", 1, 1, 14.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.AZALEA, "Azaleas", "🌿", 4, 12, 1.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.FLOWERING_AZALEA, "Flowering Azaleas", "🌺", 4, 12, 1.25D)),
            option(10, false, false, new ItemSubmissionTask(Items.AZALEA_LEAVES, "Azalea Leaves", "🍃", 8, 24, 0.6D)),
            option(10, false, false, new ItemSubmissionTask(Items.FLOWERING_AZALEA_LEAVES, "Flowering Azalea Leaves", "🌸", 8, 24, 0.75D)),
            option(4, false, false, new ItemSubmissionTask(Items.BLUE_EGG, "Blue Eggs", "Submit {count} blue eggs. The shell is doing most of the work here.", "🔵", 2, 4, 3.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.BROWN_EGG, "Brown Eggs", "Submit {count} brown eggs. Breakfast has biome variants now.", "🟤", 2, 4, 3.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.BLUE_ICE, "Blue Ice", "🧊", 8, 24, 0.75D)),
            option(2, false, false, new ItemSubmissionTask(Items.TUBE_CORAL_BLOCK, "Tube Coral Blocks", "Submit {count} living tube coral blocks. Keep them wet.", "🪸", 2, 6, 2.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.BRAIN_CORAL_FAN, "Brain Coral Fans", "Submit {count} living brain coral fans. The reef can spare a small sample.", "🧠", 2, 6, 2.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.BUBBLE_CORAL_BLOCK, "Bubble Coral Blocks", "Submit {count} living bubble coral blocks. No popping them.", "🫧", 2, 6, 2.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.FIRE_CORAL_FAN, "Fire Coral Fans", "Submit {count} living fire coral fans. They are not actually on fire.", "🔥", 2, 6, 2.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.HORN_CORAL_BLOCK, "Horn Coral Blocks", "Submit {count} living horn coral blocks from a warm reef.", "📯", 2, 6, 2.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.CALCITE, "Calcite", "Submit {count} calcite. Geodes have excellent interior walls.", "⬜", 16, 48, 0.35D)),
            option(6, false, false, new ItemSubmissionTask(Items.CUT_COPPER_STAIRS.waxed().oxidized(), "Waxed Oxidized Cut Copper Stairs", "Submit {count} waxed oxidized cut copper stairs. The colour was worth the wait.", "🟦", 2, 6, 3.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.CHISELED_RED_SANDSTONE, "Chiseled Red Sandstone", "🧱", 8, 24, 0.6D)),
            option(8, false, false, new ItemSubmissionTask(Items.CHISELED_RESIN_BRICKS, "Chiseled Resin Bricks", "Submit {count} chiseled resin bricks. Pale gardens can be colourful after all.", "🟠", 4, 12, 1.5D)),
            option(8, false, false, new ItemSubmissionTask(Items.CHISELED_SULFUR, "Chiseled Sulfur", "Submit {count} chiseled sulfur. Architecture should have a smell.", "🟡", 4, 12, 1.5D)),
            option(12, false, false, new ItemSubmissionTask(Items.COARSE_DIRT, "Coarse Dirt", "🟫", 16, 48, 0.3D)),
            option(10, false, false, new ItemSubmissionTask(Items.COPPER_HOE, "Copper Hoes", "Submit {count} copper hoes. Give the new tool tier a field test first.", "🟠", 1, 3, 4.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.COPPER_TORCH, "Copper Torches", "Submit {count} copper torches. Green fire deserves better lighting design.", "🟢", 8, 24, 0.6D)),
            option(6, false, false, new ItemSubmissionTask(Items.CREAKING_HEART, "Creaking Hearts", "Submit {count} creaking hearts. The forest will notice.", "🫀", 1, 2, 12.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().unaffected(), "Copper Golem Statues", "Submit {count} copper golem statues. Let one pose before it goes.", "🗿", 1, 3, 6.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().exposed(), "Exposed Copper Golem Statues", "🗿", 1, 3, 7.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().weathered(), "Weathered Copper Golem Statues", "🗿", 1, 3, 8.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().oxidized(), "Oxidized Copper Golem Statues", "🗿", 1, 3, 9.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().unaffected(), "Waxed Copper Golem Statues", "🗿", 1, 3, 7.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().exposed(), "Waxed Exposed Copper Golem Statues", "🗿", 1, 3, 8.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().weathered(), "Waxed Weathered Copper Golem Statues", "🗿", 1, 3, 9.0D)),
            option(2, false, false, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().oxidized(), "Waxed Oxidized Copper Golem Statues", "🗿", 1, 3, 10.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.DEEPSLATE_COAL_ORE, "Deepslate Coal Ore", "Submit {count} deepslate coal ore. Silk Touch a geological souvenir.", "⬛", 2, 6, 2.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.DEEPSLATE_IRON_ORE, "Deepslate Iron Ore", "⛏️", 2, 6, 2.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.DEEPSLATE_GOLD_ORE, "Deepslate Gold Ore", "🟨", 2, 6, 2.5D)),
            option(4, false, false, new ItemSubmissionTask(Items.DEEPSLATE_LAPIS_ORE, "Deepslate Lapis Ore", "🔵", 2, 6, 2.5D)),
            option(4, false, false, new ItemSubmissionTask(Items.DEEPSLATE_DIAMOND_ORE, "Deepslate Diamond Ore", "💎", 1, 3, 7.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.DIORITE_WALL, "Diorite Walls", "🧱", 16, 48, 0.3D)),
            option(12, false, false, new ItemSubmissionTask(Items.ANDESITE_STAIRS, "Andesite Stairs", "🪨", 16, 48, 0.3D)),
            option(80, false, false, new ItemSubmissionTask(Items.EMERALD, "Emeralds", "Convert {count} emeralds into dabloons. Villagers need not know.", "💚", 16, 48, 0.5D)),
            option(80, false, false, new ItemSubmissionTask(Items.EMERALD_BLOCK, "Emerald Blocks", "Convert {count} emerald blocks into dabloons. This is the compact exchange counter.", "🟩", 2, 6, 5.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.GLOW_LICHEN, "Glow Lichen", "✨", 16, 48, 0.4D)),
            option(12, true, false, new ItemSubmissionTask(Items.GLOWSTONE, "Glowstone", "Submit {count} glowstone. The ceiling did not need all of it.", "🌟", 8, 24, 1.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.HANGING_ROOTS, "Hanging Roots", "🌱", 8, 24, 0.75D)),
            option(12, false, false, new ItemSubmissionTask(Items.JACK_O_LANTERN, "Jack o'Lanterns", "Submit {count} jack o'lanterns. Keep one outside for atmosphere.", "🎃", 4, 12, 1.5D)),
            option(12, false, false, new ItemSubmissionTask(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, "Light Weighted Pressure Plates", "Submit {count} light weighted pressure plates. Gold can do redstone too.", "🟨", 2, 6, 2.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.MANGROVE_CHEST_BOAT, "Mangrove Chest Boats", "Submit {count} mangrove chest boats. Storage has never been so seaworthy.", "🛶", 1, 2, 6.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.MANGROVE_PROPAGULE, "Mangrove Propagules", "🌱", 8, 24, 0.6D)),
            option(10, false, false, new ItemSubmissionTask(Items.MUSHROOM_STEM, "Mushroom Stems", "Submit {count} mushroom stems. Giant mushrooms have excellent beams.", "🍄", 8, 24, 0.75D)),
            option(8, false, false, new ItemSubmissionTask(Items.POTENT_SULFUR, "Potent Sulfur", "Submit {count} potent sulfur. Handle the concentrated stuff carefully.", "⚗️", 2, 6, 3.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.PRISMARINE, "Prismarine", "Submit {count} prismarine. Monument green works outside the ocean too.", "🌊", 8, 24, 0.75D)),
            option(12, false, false, new ItemSubmissionTask(Items.PRISMARINE_SHARD, "Prismarine Shards", "🔱", 12, 32, 0.5D)),
            option(12, false, false, new ItemSubmissionTask(Items.PRISMARINE_CRYSTALS, "Prismarine Crystals", "💠", 8, 24, 0.75D)),
            option(12, false, false, new ItemSubmissionTask(Items.PUFFERFISH, "Pufferfish", "Submit {count} pufferfish. Please do not make lunch with them.", "🐡", 2, 8, 1.5D)),
            option(10, false, false, new ItemSubmissionTask(Items.RAW_GOLD_BLOCK, "Blocks of Raw Gold", "🟨", 2, 6, 4.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.RAW_IRON_BLOCK, "Blocks of Raw Iron", "⬜", 2, 6, 3.0D)),
            option(20, false, false, new ItemSubmissionTask(Items.DIAMOND_BLOCK, "Diamond Blocks", "Convert {count} diamond blocks into dabloons. There is no raw diamond block, so polished wealth will do.", "💎", 1, 3, 10.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.SLIME_BLOCK, "Slime Blocks", "Submit {count} slime blocks. Bounce on them before packing them up.", "🟩", 2, 8, 2.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.SLIME_BALL, "Slimeballs", "🟢", 12, 32, 0.5D)),
            option(10, true, false, new ItemSubmissionTask(Items.SPECTRAL_ARROW, "Spectral Arrows", "Submit {count} spectral arrows. Everything looks better with an outline.", "🏹", 8, 24, 0.75D)),
            option(8, false, false, new ItemSubmissionTask(Items.SPONGE, "Sponges", "Submit {count} dry sponges. Ocean monuments have unusual cleaning cupboards.", "🧽", 1, 4, 5.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.WET_SPONGE, "Wet Sponges", "Submit {count} wet sponges. The furnace can have the next batch.", "💧", 1, 4, 5.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.SPRUCE_TRAPDOOR, "Spruce Trapdoors", "Submit {count} spruce trapdoors. Builders know these are wall panels.", "🪵", 8, 24, 0.6D)),
            option(4, false, false, new ItemSubmissionTask(Items.SUSPICIOUS_SAND, "Suspicious Sand", "Submit one suspicious sand block. Try not to shake the evidence.", "🏜️", 1, 1, 12.0D)),
            option(4, false, false, new ItemSubmissionTask(Items.SUSPICIOUS_GRAVEL, "Suspicious Gravel", "Submit one suspicious gravel block. Suspicion is heavier than it looks.", "🪨", 1, 1, 12.0D)),
            option(8, false, false, new ItemSubmissionTask(Items.TADPOLE_BUCKET, "Buckets of Tadpole", "Submit one bucket of tadpole. Small frog, large travel plans.", "🪣", 1, 1, 12.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.TNT_MINECART, "Minecarts with TNT", "Submit {count} minecarts with TNT. No test drive is necessary.", "💣", 1, 3, 5.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.FURNACE_MINECART, "Minecarts with Furnaces", "Submit {count} minecarts with furnaces. Powered rail is not the only answer.", "🚂", 1, 3, 5.0D)),
            option(8, false, false, new ItemSubmissionTask(Items.TURTLE_HELMET, "Turtle Shells", "Submit one turtle shell. Ten extra seconds underwater were nice.", "🐢", 1, 1, 18.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.VINE, "Vines", "🌿", 12, 32, 0.5D)),
            option(10, false, false, new ItemSubmissionTask(Items.WRITTEN_BOOK, "Written Books", "Submit one written book. Give it a title worth shelving.", "📖", 1, 1, 12.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.PLAYER_HEAD, "Player Heads", "Submit one player head. The likeness is uncanny.", "🗿", 1, 1, 15.0D)),
            option(12, false, false, new ItemSubmissionTask(Items.EXPERIENCE_BOTTLE, "Bottles o' Enchanting", "Submit {count} bottles o' enchanting. Experience is liquid currency now.", "✨", 4, 12, 1.5D)),
            option(10, false, false, new ItemSubmissionTask(Items.FIREFLY_BUSH, "Firefly Bushes", "Submit {count} firefly bushes. Save the next patch for a glowing garden.", "✨", 4, 12, 1.25D)),
            option(8, false, false, new ItemSubmissionTask(Items.GOAT_HORN, "Goat Horns", "Submit one goat horn. Sound it once before it leaves.", "📯", 1, 1, 12.0D)),
            option(10, false, false, new ItemSubmissionTask(Items.NAUTILUS_SHELL, "Nautilus Shells", "Submit {count} nautilus shells. A conduit is only eight shells away.", "🐚", 2, 6, 3.0D)),
            option(5, false, false, new ItemSubmissionTask(Items.SNIFFER_EGG, "Sniffer Eggs", "Submit one sniffer egg. The ancient seed detective can hatch next time.", "🥚", 1, 1, 18.0D)),
            option(6, false, false, new ItemSubmissionTask(Items.OMINOUS_BOTTLE, "Ominous Bottles", "Submit {count} ominous bottles. Keep the bad decisions corked.", "🍾", 1, 3, 7.0D)),
            option(5, false, false, new ItemSubmissionTask(Items.HEART_OF_THE_SEA, "Hearts of the Sea", "Submit one heart of the sea. The ocean keeps strange treasure.", "💙", 1, 1, 18.0D)),

            // Banner patterns are collectors' targets, not a reason to see banners every week.
            option(1, false, false, new ItemSubmissionTask(Items.FLOWER_BANNER_PATTERN, "Flower Banner Pattern", "🌼", 1, 1, 12.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.CREEPER_BANNER_PATTERN, "Creeper Banner Pattern", "💥", 1, 1, 14.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.SKULL_BANNER_PATTERN, "Skull Banner Pattern", "💀", 1, 1, 16.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.MOJANG_BANNER_PATTERN, "Thing Banner Pattern", "🍎", 1, 1, 18.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.GLOBE_BANNER_PATTERN, "Globe Banner Pattern", "🌍", 1, 1, 14.0D)),
            option(1, true, false, new ItemSubmissionTask(Items.PIGLIN_BANNER_PATTERN, "Snout Banner Pattern", "🐽", 1, 1, 16.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.FLOW_BANNER_PATTERN, "Flow Banner Pattern", "🌊", 1, 1, 18.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.GUSTER_BANNER_PATTERN, "Guster Banner Pattern", "🌬️", 1, 1, 18.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.FIELD_MASONED_BANNER_PATTERN, "Field Masoned Banner Pattern", "🧱", 1, 1, 14.0D)),
            option(1, false, false, new ItemSubmissionTask(Items.BORDURE_INDENTED_BANNER_PATTERN, "Bordure Indented Banner Pattern", "🚩", 1, 1, 14.0D)),

            // Custom items use exact component matching. Filled or modified items cannot be consumed by mistake.
            option(10, false, false, custom("1-leaf-clover", "One-Leaf Clovers", "Submit {count} one-leaf clovers. Luck has to start somewhere.", "☘️", 4, 12, 1.0D)),
            option(8, false, false, custom("2-leaf-clover", "Two-Leaf Clovers", "Submit {count} two-leaf clovers. Twice the leaves, perhaps twice the luck.", "☘️", 2, 8, 2.0D)),
            option(6, false, false, custom("3-leaf-clover", "Three-Leaf Clovers", "Submit {count} three-leaf clovers. Almost famously lucky.", "☘️", 1, 4, 4.0D)),
            option(3, false, false, custom("4-leaf-clover", "Four-Leaf Clovers", "Submit one four-leaf clover. Spend the luck while it lasts.", "🍀", 1, 1, 18.0D)),
            option(10, false, false, custom("beer", "Beer", "Submit three beers. One brewing batch should cover quality control.", "🍺", 3, 3, 4.0D)),
            option(10, false, false, custom("golden-nutritional-paste", "Golden Nutritional Paste", "Submit one portion of golden nutritional paste. Nine ingredients later, it is food, technically.", "🟨", 1, 1, 14.0D)),
            option(8, false, false, custom("soul", "Souls", "Submit {count} souls. Do not ask where the collection box goes.", "👻", 2, 6, 3.0D)),
            option(10, false, false, custom("sushi", "Sushi", "Submit {count} pieces of sushi. Freshly caught is best.", "🍣", 2, 8, 2.0D)),
            option(6, false, false, custom("charm-sculk-phial", "Empty Sculk Phials", "Submit one empty sculk phial. Phials that contain experience do not count.", "🧪", 1, 1, 30.0D)),
            option(4, false, false, custom("disc-9am", "9AM Disc", "Submit one 9AM disc. Give it one last spin first.", "🌅", 1, 1, 22.0D)),
            option(4, false, false, custom("disc-death", "Death Disc", "Submit one Death disc. The title is not an instruction.", "💀", 1, 1, 24.0D)),
            option(4, false, false, custom("disc-dog", "Dog Disc", "Submit one Dog disc. The jukebox will miss it.", "🐕", 1, 1, 22.0D)),
            option(4, false, false, custom("disc-droopy-likes-ricochet", "Droopy Likes Ricochet Disc", "Submit one Droopy Likes Ricochet disc. A long title deserves one last play.", "💿", 1, 1, 24.0D)),
            option(4, false, false, custom("disc-droopy-likes-your-face", "Droopy Likes Your Face Disc", "Submit one Droopy Likes Your Face disc. Droopy has excellent taste.", "💿", 1, 1, 24.0D)),
            option(6, false, false, custom("cookie-jar", "Cookie Jars", "Submit one cookie jar. Keep the next one on the kitchen counter.", "🍪", 1, 1, 12.0D)),
            option(6, false, false, custom("firefly-jar", "Firefly Jars", "Submit one firefly jar. Warm light needs no redstone.", "✨", 1, 1, 12.0D)),
            option(6, false, false, custom("fruit-bowl", "Fruit Bowls", "Submit one fruit bowl. Empty tables are a design choice, but not a good one.", "🍎", 1, 1, 12.0D)),
            option(6, false, false, custom("kettle", "Kettles", "Submit one kettle. Every build deserves a tea break.", "🫖", 1, 1, 12.0D)),
            option(3, false, false, custom("spoons-carpet-grandiloquent", "Grandiloquent Spoons Carpets", "Submit one grandiloquent spoons carpet. Subtlety was never the point.", "🥄", 1, 1, 12.0D)),
            option(3, false, false, custom("spoons-carpet-junoesque", "Junoesque Spoons Carpets", "Submit one junoesque spoons carpet. The floor has standards.", "🥄", 1, 1, 12.0D)),
            option(3, false, false, custom("spoons-carpet-meretricious", "Meretricious Spoons Carpets", "Submit one meretricious spoons carpet. Taste is subjective.", "🥄", 1, 1, 12.0D)),
            option(6, false, false, custom("vinyl-player", "Vinyl Players", "Submit one vinyl player. Its music disc makes this more than a furniture order.", "📻", 1, 1, 30.0D)),

            // Food
            option(2, false, false, new EatItemTask(Items.BREAD, "Bread", "🍞", 5, 10, 0.75D)),
            option(2, false, false, new EatItemTask(Items.BEETROOT, "Beetroot", "🫜", 8, 16, 0.5D)),
            option(2, false, false, new EatItemTask(Items.COOKIE, "Cookies", "🍪", 6, 12, 0.6D)),
            option(2, false, false, new EatItemTask(Items.BAKED_POTATO, "Baked Potatoes", "🥔", 5, 10, 0.75D)),
            option(2, false, true, new EatItemTask(Items.CHORUS_FRUIT, "Chorus Fruit", "🟣", 3, 7, 1.5D)),
            option(2, false, false, new EatItemTask(Items.CAKE, "Cake Slices", "🎂", 2, 6, 1.0D)),
            option(2, false, false, new EatItemTask(Items.SUSPICIOUS_STEW, "Suspicious Stew", "🥣", 1, 2, 4.0D)),
            option(2, false, false, new EatItemTask(Items.RABBIT_STEW, "Rabbit Stew", "🐇", 1, 2, 4.0D)),
            option(2, false, false, new EatItemTask(Items.PUMPKIN_PIE, "Pumpkin Pie", "🥧", 2, 4, 2.0D)),
            option(2, false, false, new EatItemTask(Items.BEETROOT_SOUP, "Beetroot Soup", "🥣", 1, 3, 2.5D)),
            option(2, false, false, new EatItemTask(Items.HONEY_BOTTLE, "Honey Bottles", "🍯", 1, 3, 3.0D)),
            option(2, false, false, new EatItemTask(Items.MUSHROOM_STEW, "Mushroom Stew", "🍄", 1, 3, 2.5D)),
            option(2, false, false, new EatItemTask(Items.DRIED_KELP, "Dried Kelp", "🌿", 8, 16, 0.5D)),
            option(1, false, false, new EatItemTask(Items.PUFFERFISH, "Pufferfish", "🐡", 1, 1, 6.0D)),
            option(1, false, false, new EatItemTask(Items.TROPICAL_FISH, "Tropical Fish", "🐠", 1, 2, 4.0D)),
            option(2, false, false, new EatItemTask(Items.POISONOUS_POTATO, "Poisonous Potatoes", "🥔", 1, 1, 5.0D)),

            // Enchanting. Other good specific targets include Silk Touch, Respiration, and Piercing.
            option(3, false, false, new EnchantAtTableTask()),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.UNBREAKING, "Unbreaking", "🔨", 7.0D)),
            option(1, false, false, new EnchantWithEnchantmentTask(Enchantments.BINDING_CURSE, "Curse of Binding", "⛓️", 8.0D)),
            option(1, false, false, new EnchantWithEnchantmentTask(Enchantments.VANISHING_CURSE, "Curse of Vanishing", "💨", 8.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.EFFICIENCY, "Efficiency", "⛏️", 6.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.FEATHER_FALLING, "Feather Falling", "🪶", 6.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.PROTECTION, "Protection", "🛡️", 6.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.FLAME, "Flame", "🔥", 7.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.FORTUNE, "Fortune", "🍀", 7.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.INFINITY, "Infinity", "♾️", 7.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.LUCK_OF_THE_SEA, "Luck of the Sea", "🎣", 6.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.LURE, "Lure", "🪝", 6.0D)),
            option(1, true, false, new EnchantWithEnchantmentTask(Enchantments.SOUL_SPEED, "Soul Speed", "👢", 14.0D)),
            option(1, false, false, new EnchantWithEnchantmentTask(Enchantments.SWIFT_SNEAK, "Swift Sneak", "🤫", 14.0D)),
            option(1, false, false, new EnchantWithEnchantmentTask(Enchantments.WIND_BURST, "Wind Burst", "🌪️", 15.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.SILK_TOUCH, "Silk Touch", "🧵", 7.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.RESPIRATION, "Respiration", "🫧", 7.0D)),
            option(2, false, false, new EnchantWithEnchantmentTask(Enchantments.PIERCING, "Piercing", "🏹", 6.0D)),

            // Uncommon hostile mobs and bosses
            option(1, false, false, new KillEntityTask(EntityTypes.ALLAY, "an Allay", "Allays", "Defeat an allay. It was probably plotting something melodious.", "🧚", 1, 1, 8.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.ARMADILLO, "an Armadillo", "Armadillos", "Defeat an armadillo. First, convince it to stop being a cube.", "🦔", 1, 1, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.AXOLOTL, "an Axolotl", "Axolotls", "Defeat an axolotl. You monster.", "🦎", 1, 1, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.CAT, "a Cat", "Cats", "Defeat a cat. It has eight more lives anyway.", "🐈", 1, 1, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.DOLPHIN, "a Dolphin", "Dolphins", "Defeat a dolphin. Expect poor reviews from the ocean.", "🐬", 1, 1, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.DONKEY, "a Donkey", "Donkeys", "Defeat a donkey. Check the saddlebags first.", "🫏", 1, 1, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.GLOW_SQUID, "a Glow Squid", "Glow Squid", "Defeat {count} glow squid. Their campaign promise has expired.", "🦑", 2, 5, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.MOOSHROOM, "a Mooshroom", "Mooshrooms", "Defeat a mooshroom. Finding the island was the hard part.", "🍄", 1, 1, 14.0D)),
            option(2, false, false, new KillEntityTask(EntityTypes.SNIFFER, "a Sniffer", "Sniffers", "Defeat a sniffer. This task has a personal vendetta.", "🐽", 1, 1, 10.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.TRADER_LLAMA, "a Trader Llama", "Trader Llamas", "Defeat a trader llama. Duck before it files a complaint.", "🦙", 1, 1, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.NAUTILUS, "a Nautilus", "Nautiluses", "Defeat a nautilus. The spiral did nothing wrong.", "🐚", 1, 1, 9.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.ZOMBIE_NAUTILUS, "a Zombie Nautilus", "Zombie Nautiluses", "Defeat a zombie nautilus and end its second voyage.", "🧟", 1, 1, 12.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.HAPPY_GHAST, "a Happy Ghast", "Happy Ghasts", "Defeat a happy ghast. Happiness was temporary.", "😊", 1, 1, 14.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.BLAZE, "a Blaze", "Blazes", "🔥", 4, 8, 2.5D)),
            option(1, false, false, new KillEntityTask(EntityTypes.BOGGED, "a Bogged", "Bogged", "🏹", 2, 4, 4.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.BREEZE, "a Breeze", "Breezes", "🌬️", 2, 4, 4.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.CREEPER, "a Creeper", "Creepers", "💥", 3, 7, 2.5D)),
            option(1, false, false, new KillEntityTask(EntityTypes.GUARDIAN, "a Guardian", "Guardians", "🔱", 3, 7, 3.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.MAGMA_CUBE, "a Magma Cube", "Magma Cubes", "🟧", 4, 10, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.PARCHED, "a Parched", "Parched", "🏜️", 1, 2, 6.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.PIGLIN, "a Piglin", "Piglins", "🐽", 3, 6, 2.5D)),
            option(1, true, false, new KillEntityTask(EntityTypes.PIGLIN_BRUTE, "a Piglin Brute", "Piglin Brutes", "🪓", 1, 2, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.PILLAGER, "a Pillager", "Pillagers", "🏴", 3, 8, 2.5D)),
            option(1, false, false, new KillEntityTask(EntityTypes.SKELETON, "a Skeleton", "Skeletons", "💀", 4, 9, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.SLIME, "a Slime", "Slimes", "🟩", 6, 15, 1.25D)),
            option(1, false, false, new KillEntityTask(EntityTypes.SPIDER, "a Spider", "Spiders", "🕷️", 3, 8, 2.5D)),
            option(1, false, false, new KillEntityTask(EntityTypes.SULFUR_CUBE, "a Sulfur Cube", "Sulfur Cubes", "Defeat {count} sulfur cubes and watch them split.", "🟨", 2, 5, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.VEX, "a Vex", "Vexes", "🪽", 1, 2, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.VINDICATOR, "a Vindicator", "Vindicators", "🪓", 1, 2, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.WARDEN, "the Warden", "Wardens", "Defeat the Warden. Sneaking away remains the sensible option.", "📡", 1, 1, 25.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.WITHER_SKELETON, "a Wither Skeleton", "Wither Skeletons", "☠️", 3, 6, 3.5D)),
            option(1, true, false, new KillEntityTask(EntityTypes.ZOGLIN, "a Zoglin", "Zoglins", "🐗", 1, 2, 6.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.ZOMBIE, "a Zombie", "Zombies", "🧟", 4, 10, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.ZOMBIE_VILLAGER, "a Zombie Villager", "Zombie Villagers", "🧟", 1, 2, 5.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.ZOMBIFIED_PIGLIN, "a Zombified Piglin", "Zombified Piglins", "🧟", 3, 7, 2.5D)),
            option(1, false, false, new KillEntityTask(EntityTypes.HUSK, "a Husk", "Husks", "🏜️", 2, 5, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.STRAY, "a Stray", "Strays", "🏹", 2, 4, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.DROWNED, "a Drowned", "Drowned", "🔱", 2, 5, 4.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.WITCH, "a Witch", "Witches", "🧙", 1, 3, 7.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.PHANTOM, "a Phantom", "Phantoms", "🌙", 1, 4, 5.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.SILVERFISH, "a Silverfish", "Silverfish", "🪲", 3, 8, 2.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.CAVE_SPIDER, "a Cave Spider", "Cave Spiders", "🕷️", 2, 5, 4.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.ELDER_GUARDIAN, "an Elder Guardian", "Elder Guardians", "🐟", 1, 1, 20.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.RAVAGER, "a Ravager", "Ravagers", "🐂", 1, 2, 10.0D)),
            option(1, false, false, new KillEntityTask(EntityTypes.EVOKER, "an Evoker", "Evokers", "🪄", 1, 1, 15.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.GHAST, "a Ghast", "Ghasts", "👻", 1, 3, 7.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.HOGLIN, "a Hoglin", "Hoglins", "🐗", 2, 5, 4.0D)),
            option(1, true, false, new KillEntityTask(EntityTypes.WITHER, "the Wither", "Withers", "💀", 1, 1, 28.0D)),
            option(1, false, true, new KillEntityTask(EntityTypes.ENDERMITE, "an Endermite", "Endermites", "🟣", 2, 5, 5.0D)),
            option(1, false, true, new KillEntityTask(EntityTypes.SHULKER, "a Shulker", "Shulkers", "📦", 2, 5, 6.0D)),
            option(1, false, true, new KillEntityTask(EntityTypes.ENDER_DRAGON, "the Ender Dragon", "Ender Dragons", "🐉", 1, 1, 30.0D)),

            // Effects
            option(1, false, false, new ReceiveEffectTask(MobEffects.SPEED, "Speed", "💨", 3.0D)),
            option(1, false, false, new ReceiveEffectTask(MobEffects.DARKNESS, "Darkness", "🌑", 8.0D)),
            option(1, false, true, new ReceiveEffectTask(MobEffects.LEVITATION, "Levitation", "🎈", 8.0D)),
            option(1, false, false, new ReceiveEffectTask(MobEffects.GLOWING, "Glowing", "✨", 6.0D)),
            option(1, false, false, new ReceiveEffectTask(MobEffects.NAUSEA, "Nausea", "🌀", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.DOLPHINS_GRACE, "Dolphin's Grace", "🐬", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.BREATH_OF_THE_NAUTILUS, "Breath of the Nautilus", "🐚", 6.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.BAD_OMEN, "Bad Omen", "🏴", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.RAID_OMEN, "Raid Omen", "🏰", 6.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.TRIAL_OMEN, "Trial Omen", "🔑", 6.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.HASTE, "Haste", "⛏️", 6.0D)),
            option(2, true, false, new ReceiveEffectTask(MobEffects.INVISIBILITY, "Invisibility", "👻", 5.0D)),
            option(2, true, false, new ReceiveEffectTask(MobEffects.WATER_BREATHING, "Water Breathing", "🌊", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.WIND_CHARGED, "Wind Charged", "🌬️", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.WITHER, "Wither", "☠️", 6.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.INFESTED, "Infested", "🪲", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.OOZING, "Oozing", "🟢", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.WEAVING, "Weaving", "🕸️", 5.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.CONDUIT_POWER, "Conduit Power", "🔱", 6.0D)),
            option(2, false, false, new ReceiveEffectTask(MobEffects.NIGHT_VISION, "Night Vision", "👁️", 2.0D)),

            // Experience and breeding
            option(3, false, false, new GainLevelsTask()),
            option(2, false, false, new BreedEntityTask(EntityTypes.RABBIT, "Rabbits", "🐇", 1, 3, 3.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.COW, "Cows", "🐄", 2, 4, 3.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.SHEEP, "Sheep", "🐑", 2, 4, 3.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.PIG, "Pigs", "🐖", 2, 4, 3.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.CHICKEN, "Chickens", "🐔", 2, 5, 2.5D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.GOAT, "Goats", "🐐", 1, 2, 5.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.DONKEY, "Donkeys", "🫏", 1, 1, 8.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.WOLF, "Wolves", "🐺", 1, 2, 5.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.CAT, "Cats", "🐈", 1, 2, 5.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.AXOLOTL, "Axolotls", "🦎", 1, 1, 9.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.TURTLE, "Turtles", "🐢", 1, 1, 9.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.PANDA, "Pandas", "🐼", 1, 1, 9.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.BEE, "Bees", "🐝", 2, 4, 3.5D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.FOX, "Foxes", "🦊", 1, 2, 6.0D)),
            option(1, true, false, new BreedEntityTask(EntityTypes.STRIDER, "Striders", "🟥", 1, 2, 7.0D)),
            option(1, true, false, new BreedEntityTask(EntityTypes.HOGLIN, "Hoglins", "🐗", 1, 2, 7.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.SNIFFER, "Sniffers", "🐽", 1, 1, 10.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.FROG, "Frogs", "🐸", 1, 2, 6.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.CAMEL, "Camels", "🐫", 1, 1, 8.0D)),
            option(2, false, false, new BreedEntityTask(EntityTypes.ARMADILLO, "Armadillos", "🦔", 1, 2, 6.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.GOAT, "Goats", "🐐", 2, 6, 1.25D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.DONKEY, "Donkeys", "🫏", 2, 5, 1.5D)),
            option(3, false, false, new FeedEntityTask(EntityTypes.WOLF, "Wolves", "🐺", 2, 6, 1.25D)),
            option(3, false, false, new FeedEntityTask(EntityTypes.CAT, "Cats", "🐈", 2, 6, 1.25D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.AXOLOTL, "Axolotls", "🦎", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.RABBIT, "Rabbits", "🐇", 3, 8, 1.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.TURTLE, "Turtles", "🐢", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.PANDA, "Pandas", "🐼", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.BEE, "Bees", "🐝", 3, 8, 1.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.FOX, "Foxes", "🦊", 1, 4, 2.0D)),
            option(1, true, false, new FeedEntityTask(EntityTypes.STRIDER, "Striders", "🟥", 1, 4, 2.5D)),
            option(1, true, false, new FeedEntityTask(EntityTypes.HOGLIN, "Hoglins", "🐗", 1, 4, 2.5D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.SNIFFER, "Sniffers", "🐽", 1, 4, 2.5D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.FROG, "Frogs", "🐸", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.CAMEL, "Camels", "🐫", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.ARMADILLO, "Armadillos", "🦔", 1, 4, 2.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.COW, "Cows", "🐄", 3, 8, 1.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.SHEEP, "Sheep", "🐑", 3, 8, 1.0D)),
            option(2, false, false, new FeedEntityTask(EntityTypes.NAUTILUS, "Nautiluses", "🐚", 1, 4, 2.5D)),

            // Villager trades
            option(1, false, false, new VillagerTradeTask(Items.DYED_CANDLE.red(), "Red Candles", 1, 3, 2.0D)),
            option(1, false, false, new VillagerTradeTask(Items.STONE_HOE, "Stone Hoes", 1, 2, 3.0D)),
            option(1, false, false, new VillagerTradeTask(Items.SUSPICIOUS_STEW, "Suspicious Stew", 1, 2, 3.0D)),
            option(1, false, false, new VillagerTradeTask(Items.GLAZED_TERRACOTTA.pink(), "Pink Glazed Terracotta", 1, 3, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.MASON, "Mason", 1, 3, 2.0D)),
            option(10, false, false, new VillagerTradeTask(VillagerTradeTask.Mode.RECEIVE_EMERALDS, 8, 24, 0.5D)),
            option(10, false, false, new VillagerTradeTask(VillagerTradeTask.Mode.SPEND_EMERALDS, 8, 24, 0.5D)),
            option(2, false, false, new VillagerTradeTask(Items.GLISTERING_MELON_SLICE, "Glistering Melon Slices", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(Items.RABBIT_STEW, "Rabbit Stew", 1, 3, 2.5D)),
            option(2, false, false, new VillagerTradeTask(Items.DRIED_KELP_BLOCK, "Dried Kelp Blocks", 1, 4, 1.5D)),
            option(2, false, false, new VillagerTradeTask(Items.FISHING_ROD, "Fishing Rods", 1, 1, 6.0D)),
            option(2, false, false, new VillagerTradeTask(Items.NAME_TAG, "Name Tags", 1, 3, 3.0D)),
            option(2, false, false, new VillagerTradeTask(Items.LANTERN, "Lanterns", 2, 6, 1.25D)),
            option(2, false, false, new VillagerTradeTask(Items.BELL, "Bells", 1, 1, 7.0D)),
            option(2, false, false, new VillagerTradeTask(Items.CROSSBOW, "Crossbows", 1, 2, 3.0D)),
            option(2, false, false, new VillagerTradeTask(Items.TIPPED_ARROW, "Tipped Arrows", 5, 15, 0.5D)),
            option(3, false, false, new VillagerTradeTask(Items.FILLED_MAP, "Explorer Maps", 1, 1, 9.0D)),
            option(2, false, false, new VillagerTradeTask(Items.EXPERIENCE_BOTTLE, "Bottles o' Enchanting", 2, 8, 1.0D)),
            option(2, false, false, new VillagerTradeTask(Items.GLOWSTONE, "Glowstone", 2, 8, 1.0D)),
            option(2, false, false, new VillagerTradeTask(Items.LEATHER_HORSE_ARMOR, "Leather Horse Armor", 1, 1, 6.0D)),
            option(2, false, false, new VillagerTradeTask(Items.PAINTING, "Paintings", 1, 4, 1.5D)),
            option(2, false, false, new VillagerTradeTask(Items.BANNER.blue(), "Blue Banners", 1, 3, 2.0D)),
            option(2, false, false, new VillagerTradeTask(Items.GLAZED_TERRACOTTA.blue(), "Blue Glazed Terracotta", 2, 8, 1.0D)),
            option(2, false, false, VillagerTradeTask.give(Items.INK_SAC, "Ink Sacs", 3, 12, 0.6D)),
            option(2, false, false, VillagerTradeTask.give(Items.DIAMOND, "Diamonds", 1, 2, 4.0D)),
            option(2, false, false, VillagerTradeTask.give(Items.FLINT, "Flint", 4, 16, 0.5D)),
            option(2, false, false, VillagerTradeTask.give(Items.FEATHER, "Feathers", 6, 24, 0.35D)),
            option(2, false, false, VillagerTradeTask.give(Items.TRIPWIRE_HOOK, "Tripwire Hooks", 2, 8, 0.8D)),
            option(2, false, false, VillagerTradeTask.give(Items.ROTTEN_FLESH, "Rotten Flesh", 8, 32, 0.25D)),
            option(2, false, false, VillagerTradeTask.give(Items.RABBIT_FOOT, "Rabbit's Feet", 1, 4, 2.0D)),
            option(2, false, false, VillagerTradeTask.give(Items.RABBIT_HIDE, "Rabbit Hide", 3, 12, 0.75D)),
            option(2, false, false, VillagerTradeTask.give(Items.DYE.blue(), "Blue Dye", 4, 16, 0.5D)),
            option(2, true, false, VillagerTradeTask.give(Items.QUARTZ, "Nether Quartz", 4, 16, 0.6D)),
            option(2, false, false, VillagerTradeTask.give(Items.GRANITE, "Granite", 8, 24, 0.3D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.ARMORER, "Armorer", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.BUTCHER, "Butcher", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.CARTOGRAPHER, "Cartographer", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.CLERIC, "Cleric", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.FARMER, "Farmer", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.FISHERMAN, "Fisherman", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.FLETCHER, "Fletcher", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.LEATHERWORKER, "Leatherworker", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.LIBRARIAN, "Librarian", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.SHEPHERD, "Shepherd", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.TOOLSMITH, "Toolsmith", 1, 4, 2.0D)),
            option(2, false, false, new VillagerTradeTask(VillagerProfession.WEAPONSMITH, "Weaponsmith", 1, 4, 2.0D)),
            option(3, false, false, VillagerTradeTask.wanderingTrader(1, 3, 4.0D)),

            // Brewing
            option(2, true, false, new BrewPotionTask(Potions.NIGHT_VISION, "Night Vision", 1, 3, 3.0D)),
            option(2, true, false, new BrewPotionTask(Potions.INVISIBILITY, "Invisibility", 1, 3, 4.0D)),
            option(2, true, false, new BrewPotionTask(Potions.LEAPING, "Leaping", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.FIRE_RESISTANCE, "Fire Resistance", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.SWIFTNESS, "Swiftness", 1, 3, 3.0D)),
            option(2, true, false, new BrewPotionTask(Potions.SLOWNESS, "Slowness", 1, 3, 4.0D)),
            option(2, true, false, new BrewPotionTask(Potions.HEALING, "Healing", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.HARMING, "Harming", 1, 3, 4.0D)),
            option(2, true, false, new BrewPotionTask(Potions.POISON, "Poison", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.REGENERATION, "Regeneration", 1, 3, 4.0D)),
            option(2, true, false, new BrewPotionTask(Potions.STRENGTH, "Strength", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.WIND_CHARGED, "Wind Charging", 1, 2, 5.0D)),
            option(2, true, false, new BrewPotionTask(Potions.WEAVING, "Weaving", 1, 2, 5.0D)),
            option(2, true, false, new BrewPotionTask(Potions.OOZING, "Oozing", 1, 2, 5.0D)),
            option(2, true, false, new BrewPotionTask(Potions.INFESTED, "Infestation", 1, 2, 5.0D)),
            option(2, true, false, new BrewPotionTask(Potions.SLOW_FALLING, "Slow Falling", 1, 3, 4.0D)),
            option(2, true, false, new BrewPotionTask(Potions.TURTLE_MASTER, "the Turtle Master", 1, 2, 5.0D)),
            option(2, true, false, new BrewPotionTask(Potions.WEAKNESS, "Weakness", 1, 3, 3.5D)),
            option(2, true, false, new BrewPotionTask(Potions.WATER_BREATHING, "Water Breathing", 1, 3, 3.5D)),

            // Archaeology and fishing
            option(2, false, false, new BrushBlockTask(Blocks.SUSPICIOUS_SAND, "Suspicious Sand")),
            option(2, false, false, new BrushBlockTask(Blocks.SUSPICIOUS_GRAVEL, "Suspicious Gravel")),
            option(15, false, false, new FishTask(8, 16, 0.8D)),
            option(2, false, false, new FishTask(Items.COD, "Cod", 1, 1, 5.0D)),
            option(2, false, false, new FishTask(Items.SALMON, "Salmon", 1, 1, 5.0D)),
            option(2, false, false, new FishTask(Items.PUFFERFISH, "Pufferfish", 1, 1, 7.0D)),
            option(2, false, false, new FishTask(Items.TROPICAL_FISH, "Tropical Fish", 1, 1, 7.0D)),
            option(2, false, false, FishTask.custom("fish-albacore", "Albacore", 1, 1, 5.0D)),
            option(2, false, false, FishTask.custom("fish-bass", "Bass", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-carp", "Carp", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-anchovy", "Anchovy", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-herring", "Herring", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-perch", "Perch", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-pike", "Pike", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-tuna", "Tuna", 1, 1, 4.0D)),
            option(2, false, false, FishTask.custom("fish-red_snapper", "Red Snapper", 1, 1, 4.0D)),

            // Mining
            option(1, false, false, new BreakBlockTask(Blocks.SPAWNER, "a Spawner", "🔥", 1, 1, 12.0D)),
            option(1, false, false, new BreakBlockTask(Blocks.INFESTED_STONE, "Infested Stone", "🪲", 1, 3, 3.0D)),
            option(1, false, false, new BreakBlockTask(Blocks.BUDDING_AMETHYST, "Budding Amethyst", "💎", 1, 1, 10.0D)),
            option(1, false, false, new BreakBlockTask(Blocks.REINFORCED_DEEPSLATE, "Reinforced Deepslate", "⬛", 1, 1, 12.0D)),
            option(1, false, false, new BreakBlockTask(Blocks.EMERALD_ORE, "Emerald Ore", "💚", 1, 2, 5.0D)),
            option(1, false, true, new BreakBlockTask(Blocks.DRAGON_HEAD, "a Dragon Head", "🐉", 1, 1, 14.0D)),
            option(1, true, false, new BreakBlockTask(Blocks.WITHER_SKELETON_SKULL, "a Wither Skeleton Skull", "💀", 1, 1, 7.0D)),
            option(1, true, false, new BreakBlockTask(Blocks.LODESTONE, "a Lodestone", "🧭", 1, 1, 8.0D)),
            option(1, true, false, new BreakBlockTask(Blocks.GILDED_BLACKSTONE, "Gilded Blackstone", "🟨", 1, 2, 6.0D)),
            option(1, true, false, new BreakBlockTask(Blocks.CRYING_OBSIDIAN, "Crying Obsidian", "🟪", 1, 3, 4.0D)),
            option(1, true, false, new BreakBlockTask(Blocks.ANCIENT_DEBRIS, "Ancient Debris", "🟫", 1, 2, 8.0D)),
            option(2, false, false, new BreakBlockTask(Blocks.SEA_LANTERN, "Sea Lanterns", "💡", 2, 6, 1.5D)),
            option(1, false, true, new BreakBlockTask(Blocks.PURPUR_BLOCK, "Purpur Blocks", "🟪", 4, 12, 0.75D)),
            option(2, false, false, new BreakBlockTask(Blocks.TUBE_CORAL_BLOCK, "Tube Coral Blocks", "🪸", 1, 2, 4.0D)),
            option(2, false, false, new BreakBlockTask(Blocks.BRAIN_CORAL_BLOCK, "Brain Coral Blocks", "🧠", 1, 2, 4.0D)),
            option(2, false, false, new BreakBlockTask(Blocks.MYCELIUM, "Mycelium", "🍄", 2, 8, 1.0D)),
            option(2, false, false, new BreakBlockTask(Blocks.SPONGE, "Sponges", "🧽", 1, 2, 5.0D)),
            option(2, false, false, new BreakBlockTask(Blocks.WET_SPONGE, "Wet Sponges", "🌊", 1, 2, 5.0D)),
            option(2, true, false, new BreakBlockTask(Blocks.OCHRE_FROGLIGHT, "Ochre Froglights", "🟡", 1, 3, 3.5D)),
            option(2, true, false, new BreakBlockTask(Blocks.VERDANT_FROGLIGHT, "Verdant Froglights", "🟢", 1, 3, 3.5D)),
            option(2, true, false, new BreakBlockTask(Blocks.PEARLESCENT_FROGLIGHT, "Pearlescent Froglights", "🟣", 1, 3, 3.5D)),

            // Direct entity and world actions
            option(4, false, false, simple(DailySimpleEvent.SHEAR_SHEEP, "Shear Sheep", "Shear {count} sheep. Seasonal haircuts are important.", "🐑", 4, 10, 0.75D, "Sheared", "sheep")),
            option(4, false, false, simple(DailySimpleEvent.IGNITE_CREEPER, "Ignite Creepers", "Ignite {count} creepers and give them a moment to reconsider.", "🧨", 2, 5, 2.0D, "Ignited", "creepers")),
            option(3, true, false, simple(DailySimpleEvent.REFLECT_GHAST_FIREBALL, "Return to Sender", "Reflect a ghast fireball. Postage is already paid.", "🔥", 1, 1, 7.0D, "Reflected", "fireball")),
            option(3, false, false, new UseItemTask(Items.ENDER_PEARL, "Ender Pearls", "🟢", 2, 6, 1.25D)),
            option(4, false, false, simple(DailySimpleEvent.JUMP_SLIME_BLOCK, "Bouncy Business", "Bounce on a slime block {count} times. Build up a rhythm.", "🟩", 6, 18, 0.5D, "Bounced", "times")),
            option(3, false, false, simple(DailySimpleEvent.DEFEAT_RAID, "Defeat a Raid", "Help a village survive a raid.", "🏰", 1, 1, 12.0D, "Defeated", "raid")),
            option(3, false, false, new UseItemTask(Items.WIND_CHARGE, "Wind Charges", "💨", 3, 10, 0.8D)),
            option(3, false, false, new UseItemTask(Items.SPYGLASS, "a Spyglass", "🔭", 3, 10, 0.6D)),
            option(3, false, false, simple(DailySimpleEvent.LIGHT_TNT, "Light TNT", "Light {count} TNT with flint and steel. Stand at a professionally responsible distance.", "💥", 2, 5, 1.5D, "Lit", "TNT")),
            option(3, false, false, simple(DailySimpleEvent.RENAME_TOOL, "Name Your Tool", "Give one of your tools a nice name. It has earned one.", "🏷️", 1, 1, 5.0D, "Renamed", "tool")),
            option(3, false, false, simple(DailySimpleEvent.LIGHT_CANDLE, "Light Candles", "Light {count} candles with flint and steel and make the room feel finished.", "🕯️", 2, 6, 1.0D, "Lit", "candles")),

            // Custom potions
            option(1, false, false, new UseCharmTask(DailyCharm.DISPLACEMENT, "🧪", 12.0D)),
            option(1, false, false, new UseCharmTask(DailyCharm.RETURNING, "🏠", 10.0D)),
            option(1, false, false, new UseCharmTask(DailyCharm.RESONANCE, "📡", 12.0D)),
            option(1, false, false, new UseCharmTask(DailyCharm.INSOMNIA, "🦇", 14.0D)),

            // Riding and animal care
            option(3, false, false, new RideDistanceTask(EntityTypes.MINECART, "Minecart", "🛤️", 150, 500, 0.02D)),
            option(3, false, false, new RideDistanceTask(EntityTypes.PIG, "Pig", "🐖", 60, 200, 0.035D)),
            option(3, false, false, new RideDistanceTask(EntityTypes.HORSE, "Horse", "🐎", 250, 750, 0.015D)),
            option(3, false, false, new RideDistanceTask(EntityTypes.DONKEY, "Donkey", "🫏", 150, 500, 0.02D)),
            option(2, false, false, new RideDistanceTask(EntityTypes.LLAMA, "Llama", "🦙", 100, 350, 0.025D)),
            option(2, false, false, new RideDistanceTask(EntityTypes.SKELETON_HORSE, "Skeleton Horse", "💀", 150, 500, 0.025D)),
            option(2, false, false, new RideDistanceTask(EntityTypes.CAMEL, "Camel", "🐫", 150, 500, 0.02D)),
            option(2, false, false, new RideDistanceTask(EntityTypes.CAMEL_HUSK, "Camel Husk", "🏜️", 100, 300, 0.035D)),
            option(2, false, false, new RideDistanceTask(EntityTypes.NAUTILUS, "Nautilus", "🐚", 150, 500, 0.025D)),
            option(1, false, false, new RideDistanceTask(EntityTypes.ZOMBIE_NAUTILUS, "Zombie Nautilus", "🧟", 100, 300, 0.04D)),
            option(3, false, false, new RideDistanceTask(EntityTypes.OAK_CHEST_BOAT, "Chest Boat", "🛶", 250, 750, 0.015D)),
            option(2, true, false, new RideDistanceTask(EntityTypes.STRIDER, "Strider", "🟥", 150, 500, 0.025D)),
            option(2, true, false, new RideDistanceTask(EntityTypes.HAPPY_GHAST, "Happy Ghast", "😊", 250, 750, 0.02D)),
            option(3, false, false, simple(DailySimpleEvent.MILK_COW, "Fresh Milk", "Milk {count} cows.", "🥛", 1, 3, 2.0D, "Milked", "cows")),
            option(3, false, false, simple(DailySimpleEvent.BRUSH_ARMADILLO, "Brush an Armadillo", "Brush {count} armadillos.", "🪥", 1, 3, 2.0D, "Brushed", "armadillos")),

            // Weapon challenges.
            option(2, false, false, new KillWithItemTask(Items.CROSSBOW, "a Crossbow", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(ItemTags.SPEARS, "a Spear", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(ItemTags.AXES, "an Axe", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(ItemTags.SWORDS, "a Sword", 2, 5, 1.5D)),
            option(2, false, false, new KillWithItemTask(Items.BOW, "a Bow", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(Items.MACE, "a Mace", 1, 2, 5.0D)),
            option(2, false, false, new KillWithItemTask(Items.STICK, "a Stick", 1, 1, 7.0D)),
            option(2, false, false, new KillWithItemTask(Items.SNOWBALL, "Snowballs", 1, 1, 8.0D)),
            option(2, false, false, new KillWithItemTask(Items.EGG, "Eggs", 1, 1, 8.0D)),
            option(2, false, false, new KillWithItemTask(Items.FEATHER, "a Feather", 1, 1, 7.0D)),
            option(2, false, false, new KillWithItemTask(ItemTags.HOES, "a Hoe", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(ItemTags.PICKAXES, "a Pickaxe", 1, 3, 3.0D)),
            option(2, false, false, new KillWithItemTask(Items.TRIDENT, "a Trident", 1, 4, 2.0D)),
            option(2, false, false, new KillWithItemTask(Items.FIREWORK_ROCKET, "Fireworks", 1, 2, 5.0D)),
            option(1, false, false, new HitPlayerWithProjectileTask(EntityTypes.SNOWBALL, "Snowballs")),
            option(1, false, false, new HitPlayerWithProjectileTask(EntityTypes.EGG, "Eggs")),

            // Items, crops, and decoration
            option(2, false, false, new UseItemTask(Items.GOAT_HORN, "a Goat Horn", "📯", 1, 3, 0.75D)),
            option(3, false, false, simple(DailySimpleEvent.PLAY_MUSIC_DISC, "Play Music Discs", "Put {count} music discs in a jukebox.", "💿", 1, 3, 0.75D, "Played", "discs")),
            option(2, false, false, new PlantCropTask(Items.BEETROOT_SEEDS, "Beetroot Seeds", "🌱", 8, 18, 0.4D)),
            option(3, false, false, new PlantCropTask(Items.WHEAT_SEEDS, "Wheat Seeds", "🌾", 10, 24, 0.3D)),
            option(3, false, false, new PlantCropTask(Items.CARROT, "Carrots", "🥕", 8, 20, 0.35D)),
            option(3, false, false, new PlantCropTask(Items.POTATO, "Potatoes", "🥔", 8, 20, 0.35D)),
            option(2, false, false, new PlantCropTask(Items.PUMPKIN_SEEDS, "Pumpkin Seeds", "🎃", 6, 16, 0.45D)),
            option(2, false, false, new PlantCropTask(Items.MELON_SEEDS, "Melon Seeds", "🍉", 6, 16, 0.45D)),
            option(2, false, false, new PlantCropTask(Items.TORCHFLOWER_SEEDS, "Torchflower Seeds", "🌼", 2, 6, 1.25D)),
            option(2, false, false, new PlantCropTask(Items.PITCHER_POD, "Pitcher Pods", "🪻", 2, 6, 1.25D)),
            option(2, false, false, new PlantCropTask(Items.COCOA_BEANS, "Cocoa", "🍫", 4, 12, 0.6D)),
            option(2, false, false, new PlantCropTask(Items.SUGAR_CANE, "Sugar Cane", "🎋", 8, 20, 0.35D)),
            option(2, true, false, new PlantCropTask(Items.NETHER_WART, "Nether Wart", "🔴", 6, 16, 0.5D)),
            option(2, false, false, new CreateGolemTask(EntityTypes.COPPER_GOLEM, "Copper Golem", "🟠", 10.0D)),
            option(2, false, false, new CreateGolemTask(EntityTypes.IRON_GOLEM, "Iron Golem", "🤖", 12.0D)),
            option(2, false, false, new CreateGolemTask(EntityTypes.SNOW_GOLEM, "Snow Golem", "⛄", 6.0D)),
            option(3, false, false, simple(DailySimpleEvent.RING_BELL, "Ring a Bell", "Ring a bell {count} times. Let the neighbourhood know you found it.", "🔔", 3, 8, 0.75D, "Rang", "times")),

            // Damage
            option(2, false, false, new TakeDamageTask(DamageTypes.MAGIC, "Magic", "🪄", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.FALLING_ANVIL, "a Falling Anvil", "⚒️", 7.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.SWEET_BERRY_BUSH, "a Sweet Berry Bush", "🫐", 4.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.CACTUS, "a Cactus", "🌵", 4.0D)),
            option(1, false, false, new TakeDamageTask(DamageTypes.LIGHTNING_BOLT, "Lightning", "⚡", 10.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.WITCH, "a Witch's Harming Potion", "🧙", 8.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.DROWN, "Drowning", "🌊", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.ENDER_PEARL, "an Ender Pearl", "🟢", 4.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.FALL, "a Fall", "🪂", 4.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.FIREWORKS, "Fireworks", "🎆", 6.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.FALLING_STALACTITE, "a Falling Stalactite", "🪨", 7.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.STALAGMITE, "a Stalagmite", "📍", 6.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.IN_WALL, "Suffocation", "🧱", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.STARVE, "Starvation", "🍽️", 6.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.STING, "a Bee Sting", "🐝", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.SPIT, "Llama Spit", "🦙", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.SONIC_BOOM, "a Sonic Boom", "📡", 10.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.MACE_SMASH, "a Mace Smash", "🔨", 7.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.SPEAR, "a Spear", "🗡️", 6.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.FREEZE, "Freezing", "🥶", 5.0D)),
            option(2, true, false, new TakeDamageTask(DamageTypes.HOT_FLOOR, "a Magma Block", "🔥", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.CAMPFIRE, "a Campfire", "🏕️", 4.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.THORNS, "Thorns", "🌹", 5.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.WIND_CHARGE, "a Wind Charge", "🌬️", 5.0D)),
            option(2, true, false, new TakeDamageTask(DamageTypes.FIREBALL, "a Fireball", "🔥", 6.0D)),
            option(2, false, false, new TakeDamageTask(DamageTypes.SULFUR_CUBE_HOT, "a Hot Sulfur Cube", "🟨", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.LLAMA, "a Llama", "🦙", 5.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.BEE, "a Bee", "🐝", 5.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.WARDEN, "the Warden", "📡", 10.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.GOAT, "a Goat's Ram", "🐐", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.PUFFERFISH, "a Pufferfish", "🐡", 5.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.GUARDIAN, "a Guardian", "🔱", 7.0D)),
            option(1, false, false, new TakeDamageTask(EntityTypes.ELDER_GUARDIAN, "an Elder Guardian", "🐟", 9.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.EVOKER, "an Evoker", "🪄", 8.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.RAVAGER, "a Ravager", "🐂", 7.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.BREEZE, "a Breeze", "🌬️", 6.0D)),
            option(2, false, true, new TakeDamageTask(EntityTypes.SHULKER, "a Shulker", "📦", 8.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.PHANTOM, "a Phantom", "🌙", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.DOLPHIN, "an Angry Dolphin", "🐬", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.POLAR_BEAR, "a Polar Bear", "🐻‍❄️", 7.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.TRADER_LLAMA, "a Trader Llama", "🦙", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.VEX, "a Vex", "🪽", 7.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.ENDERMAN, "an Angry Enderman", "👁️", 6.0D)),
            option(2, false, false, new TakeDamageTask(EntityTypes.PARCHED, "a Parched", "🏜️", 7.0D)),
            option(2, true, false, new TakeDamageTask(EntityTypes.PIGLIN_BRUTE, "a Piglin Brute", "🪓", 7.0D)),
            option(2, true, false, new TakeDamageTask(EntityTypes.HOGLIN, "a Hoglin", "🐗", 7.0D)),
            option(1, true, false, new TakeDamageTask(EntityTypes.WITHER, "the Wither", "☠️", 10.0D)),
            option(1, false, true, new TakeDamageTask(EntityTypes.ENDER_DRAGON, "the Ender Dragon", "🐉", 12.0D)),

            // Block and decoration interactions
            option(4, false, false, simple(DailySimpleEvent.FILL_FLOWER_POT, "Pot Some Flowers", "Put {count} flowers in flower pots. Give an empty corner some colour.", "🌷", 2, 5, 1.25D, "Potted", "flowers")),
            option(4, false, false, simple(DailySimpleEvent.HANG_PAINTING, "Curate a Wall", "Hang {count} paintings. Find one which suits the room.", "🖼️", 1, 4, 1.5D, "Hung", "paintings")),
            option(4, false, false, simple(DailySimpleEvent.FILL_BOOKSHELF, "Stock a Bookshelf", "Put {count} books in chiseled bookshelves. A library starts with one shelf.", "📚", 2, 6, 1.0D, "Stored", "books")),
            option(3, false, false, simple(DailySimpleEvent.READ_NEW_JOKE, "Fresh Material", "Read a joke book which generates a joke you have not seen before.", "😂", 1, 1, 5.0D, "Read", "joke")),
            option(3, false, false, simple(DailySimpleEvent.KICK_SULFUR_CUBE, "Kick a Sulfur Cube", "Kick a sulfur cube. It probably had it coming.", "🟨", 1, 1, 5.0D, "Kicked", "cube")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.BASEDRUM, "Bass Drum")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.HARP, "Harp")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.SNARE, "Snare")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.FLUTE, "Flute")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.BELL, "Bell")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.GUITAR, "Guitar")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.XYLOPHONE, "Xylophone")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.COW_BELL, "Cow Bell")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.DIDGERIDOO, "Didgeridoo")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.BANJO, "Banjo")),
            option(2, false, false, new PlayNoteBlockTask(NoteBlockInstrument.TRUMPET, "Copper Trumpet")),
            option(4, false, false, simple(DailySimpleEvent.CUSTOMIZE_BANNER, "Banner Workshop", "Apply {count} patterns to banners. Make something worth hanging up.", "🚩", 3, 8, 1.0D, "Applied", "patterns")),
            option(3, false, false, new UseBlockTask(Blocks.FLETCHING_TABLE, "Fletching Table", "🏹", 4, 12, 0.75D)),
            option(3, false, false, simple(DailySimpleEvent.EYE_CONTACT_ENDERMAN, "A Dangerous Look", "Make eye contact with an Enderman. Apologise quickly.", "👁️", 1, 1, 5.0D, "Made", "eye contact")),
            option(3, false, false, simple(DailySimpleEvent.MODIFY_ITEM_FRAME, "Improve an Item Frame", "Make an item frame invisible or glowing and give an item a proper display.", "🖼️", 1, 1, 4.0D, "Improved", "frame")),

            // Crafting, activity, and curing
            option(2, false, false, new CraftItemTask(Items.CLOCK, "Clocks", "🕰️", 1, 3, 2.0D)),
            option(4, false, false, new CraftItemTask(Items.GOLDEN_DANDELION, "Golden Dandelions", "🌼", 1, 10, 5.0D)),
            option(3, false, false, new CraftItemTask(Items.SPYGLASS, "Spyglasses", "🔭", 1, 2, 4.0D)),
            option(2, false, false, new CraftItemTask(Items.CONCRETE_POWDER.blue(), "Blue Concrete Powder", "🔵", 8, 24, 0.35D)),
            option(3, false, false, new CraftItemTask(Items.DAYLIGHT_DETECTOR, "Daylight Detectors", "☀️", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.STICKY_PISTON, "Sticky Pistons", "🟩", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.PUMPKIN_PIE, "Pumpkin Pies", "🥧", 2, 5, 1.75D)),
            option(3, false, false, new CraftItemTask(Items.NOTE_BLOCK, "Note Blocks", "🎵", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.TRAPPED_CHEST, "Trapped Chests", "📦", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.BANNER.blue(), "Blue Banners", "🚩", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.DECORATED_POT, "Decorated Pots", "🏺", 1, 2, 5.0D)),
            option(3, false, false, new CraftItemTask(Items.TARGET, "Target Blocks", "🎯", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.COMPARATOR, "Comparators", "🔴", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.OBSERVER, "Observers", "👁️", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.DISPENSER, "Dispensers", "🏹", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.ARMOR_STAND, "Armor Stands", "🛡️", 1, 3, 3.0D)),
            option(3, false, false, new CraftItemTask(Items.LOOM, "Looms", "🧶", 1, 3, 2.0D)),
            option(3, false, false, new CraftItemTask(Items.CARTOGRAPHY_TABLE, "Cartography Tables", "🗺️", 1, 3, 2.0D)),
            option(3, false, false, new CraftItemTask(Items.CAMPFIRE, "Campfires", "🏕️", 1, 4, 2.0D)),
            option(3, false, false, new CraftItemTask(Items.SCAFFOLDING, "Scaffolding", "🏗️", 8, 100, 0.35D)),
            option(3, false, false, new CraftItemTask(Items.FIREWORK_STAR, "Firework Stars", "🎆", 2, 8, 1.0D)),
            option(2, false, false, new CraftItemTask(Items.RECOVERY_COMPASS, "Recovery Compasses", "🧭", 1, 1, 9.0D)),
            option(3, false, false, new CraftItemTask(Items.BRUSH, "Brushes", "🖌️", 1, 4, 2.0D)),
            option(3, false, false, new PlayTimeTask()),
            option(1, true, false, new CureZombieVillagerTask())
    );

    private DailyTaskRegistry() {
    }

    public static void validate() {
        Set<String> ids = new HashSet<>();
        Set<Class<?>> usedFamilies = new HashSet<>();
        for (Option option : TASKS) {
            DailyTaskDefinition definition = option.definition();
            usedFamilies.add(definition.getClass());
            if (!ids.add(definition.getId())) {
                throw new IllegalStateException("Duplicate daily task id: " + definition.getId());
            }
            JsonObject task = definition.create(new Random(definition.getId().hashCode()));
            if (!task.get("id").getAsString().equals(definition.getId())
                    || task.get("name").getAsString().isBlank()
                    || task.get("description").getAsString().isBlank()
                    || task.get("emoji").getAsString().isBlank()
                    || task.get("current").getAsInt() != 0
                    || task.get("max").getAsInt() == 0
                    || task.get("rewardDabloons").getAsInt() != definition.getReward(task)) {
                throw new IllegalStateException("Invalid daily task definition: " + definition.getId());
            }
        }
        if (!usedFamilies.equals(FAMILY_WEIGHTS.keySet())) {
            throw new IllegalStateException("Daily task family weights do not match the catalog");
        }
    }

    public static List<JsonObject> pick(String seed, int count) {
        List<Option> available = new ArrayList<>(TASKS.stream()
                .filter(option -> (!option.nether() || NETHER_ENABLED) && (!option.end() || END_ENABLED))
                .toList());
        long availableTypes = available.stream().map(option -> option.definition().getClass()).distinct().count();
        if (count < 1 || count > availableTypes) {
            throw new IllegalArgumentException("Invalid daily task count: " + count);
        }

        Random random = new Random(seed.hashCode());
        List<JsonObject> result = new ArrayList<>(count);
        Map<Class<?>, List<Option>> families = available.stream().collect(Collectors.groupingBy(
                option -> option.definition().getClass(),
                LinkedHashMap::new,
                Collectors.toCollection(ArrayList::new)
        ));
        while (result.size() < count) {
            int familyRoll = random.nextInt(families.keySet().stream().mapToInt(FAMILY_WEIGHTS::get).sum());
            for (Class<?> family : List.copyOf(families.keySet())) {
                familyRoll -= FAMILY_WEIGHTS.get(family);
                if (familyRoll < 0) {
                    List<Option> variants = families.remove(family);
                    int variantRoll = random.nextInt(variants.stream().mapToInt(Option::weight).sum());
                    for (Option variant : variants) {
                        variantRoll -= variant.weight();
                        if (variantRoll < 0) {
                            result.add(variant.definition().create(random));
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    public static DailyTaskDefinition find(String id) {
        return TASKS.stream()
                .map(Option::definition)
                .filter(task -> task.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static JsonObject parse(String json) {
        if (json == null || json.length() > 16_384) throw new IllegalArgumentException("Daily task JSON is invalid");
        JsonObject task = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        String id = task.get("id").getAsString();
        int current = task.get("current").getAsInt();
        int max = task.get("max").getAsInt();
        int reward = task.get("rewardDabloons").getAsInt();
        if (find(id) == null || current < 0 || reward < 0 || max == 0 || max < -1 || (max > 0 && current > max)) {
            throw new IllegalArgumentException("Daily task JSON is invalid");
        }
        return task;
    }

    private static SimpleEventTask simple(
            DailySimpleEvent event,
            String name,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerIteration,
            String progressLabel,
            String progressUnit
    ) {
        return new SimpleEventTask(event, name, description, emoji, minimum, maximum, rewardPerIteration, progressLabel, progressUnit);
    }

    private static ItemSubmissionTask sherd(Item item, String name, String emoji) {
        return new ItemSubmissionTask(
                item,
                name,
                "Submit one " + name.toLowerCase() + ". Put the next one on a decorated pot.",
                emoji,
                1,
                1,
                18.0D
        );
    }

    private static ItemSubmissionTask flower(Item item, String name, String emoji) {
        return new ItemSubmissionTask(
                item,
                name,
                "Submit {count} " + name.toLowerCase() + ". Keep a few and add some colour to a build.",
                emoji,
                6,
                14,
                1.0D
        );
    }

    private static ItemSubmissionTask custom(
            String fakeItemId,
            String name,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        return ItemSubmissionTask.custom(
                fakeItemId,
                name,
                description,
                emoji,
                minimum,
                maximum,
                rewardPerItem
        );
    }

    private static ItemSubmissionTask disc(Item item, String name, String emoji, double reward) {
        return new ItemSubmissionTask(
                item,
                name,
                "Submit one " + name + ". Give it one last spin first.",
                emoji,
                1,
                1,
                reward
        );
    }

    private static ItemSubmissionTask trim(Item item, String name, String emoji, double reward) {
        return new ItemSubmissionTask(
                item,
                name,
                "Submit one " + name.toLowerCase() + " template. Copy it first if you want to keep the pattern.",
                emoji,
                1,
                1,
                reward
        );
    }

    private static Option option(int weight, boolean nether, boolean end, DailyTaskDefinition definition) {
        return new Option(weight, nether, end, definition);
    }

    private record Option(int weight, boolean nether, boolean end, DailyTaskDefinition definition) {
        private Option {
            if (weight < 1) throw new IllegalArgumentException("Daily task weights must be positive");
        }
    }
}
