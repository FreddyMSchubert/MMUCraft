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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks.*;

import java.util.ArrayList;
import java.util.Collection;
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
            Map.entry(ItemSubmissionTask.class, 25),
            Map.entry(EatItemTask.class, 5),
            Map.entry(EnchantAtTableTask.class, 2),
            Map.entry(EnchantItemTask.class, 2),
            Map.entry(KillEntityTask.class, 4),
            Map.entry(ReceiveEffectTask.class, 4),
            Map.entry(GainLevelsTask.class,  5),
            Map.entry(BreedEntityTask.class, 3),
            Map.entry(FeedEntityTask.class, 3),
            Map.entry(VillagerTradeTask.class, 5),
            Map.entry(BrewPotionTask.class, 3),
            Map.entry(BrushBlockTask.class, 2),
            Map.entry(FishTask.class, 6),
            Map.entry(BreakBlockTask.class, 2),
            Map.entry(SimpleEventTask.class, 20),
            Map.entry(UseItemTask.class, 3),
            Map.entry(UseCharmTask.class, 3),
            Map.entry(RideDistanceTask.class, 5),
            Map.entry(KillWithItemTask.class, 4),
            Map.entry(HitPlayerWithProjectileTask.class, 2),
            Map.entry(PlantCropTask.class, 3),
            Map.entry(CreateGolemTask.class, 3),
            Map.entry(TakeDamageTask.class, 5),
            Map.entry(PlayNoteBlockTask.class, 2),
            Map.entry(UseBlockTask.class, 2),
            Map.entry(CraftItemTask.class, 6),
            Map.entry(PlayTimeTask.class, 5),
            Map.entry(CureZombieVillagerTask.class, 2)
    );

    private static final List<Option> TASKS = List.of(
            // Item submissions
            option(16, false, false, 5, new ItemSubmissionTask(Items.POISONOUS_POTATO, "Poisonous Potatoes", "🥔", 4, 10, 1.5D), "Submit Poisonous Potatoes", "Submit {count} poisonous potatoes. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.DEAD_BUSH, "Dead Bushes", "🌵", 4, 12, 1.25D), "Submit Dead Bushes", "Submit {count} dead bushes. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.TINTED_GLASS, "Tinted Glass", "🪟", 4, 10, 1.5D), "Submit Tinted Glass", "Submit {count} tinted glass. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.SUNFLOWER, "Sunflowers", "🌻"), "Submit Sunflowers", "Submit {count} sunflowers. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.GLOW_INK_SAC, "Glow Ink Sacs", "🦑", 6, 16, 1.0D), "Submit Glow Ink Sacs", "Submit {count} glow ink sacs. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.HONEYCOMB, "Honeycomb", "🍯", 8, 20, 0.8D), "Submit Honeycomb", "Submit {count} honeycomb. Hold the items in your inventory, then click Claim."),
            option(12, true, false, 8, new ItemSubmissionTask(Items.BLAZE_ROD, "Blaze Rods", "🔥", 4, 10, 2.0D), "Submit Blaze Rods", "Submit {count} blaze rods. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.CANDLE, "Candles", "Submit {count} candles. Keep a few and make somewhere cosy.", "🕯️", 4, 10, 1.5D), "Submit Candles", "Submit {count} candles. Keep a few and make somewhere cosy. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.DYED_CANDLE.blue(), "Blue Candles", "Submit {count} blue candles. Blue light is still light.", "🔵", 3, 8, 2.0D), "Submit Blue Candles", "Submit {count} blue candles. Blue light is still light. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.TURTLE_EGG, "Turtle Eggs", "Submit one turtle egg without letting anything stomp on it.", "🐢", 1, 1, 15.0D), "Submit Turtle Eggs", "Submit one turtle egg without letting anything stomp on it. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.SPYGLASS, "Spyglasses", "Submit one spyglass. The horizon can wait.", "🔭", 1, 1, 12.0D), "Submit Spyglasses", "Submit one spyglass. The horizon can wait. Hold the items in your inventory, then click Claim."),
            option(16, false, false, 5, new ItemSubmissionTask(Items.AMETHYST_SHARD, "Amethyst Shards", "Submit {count} amethyst shards. Geodes grow more.", "💎", 12, 32, 0.5D), "Submit Amethyst Shards", "Submit {count} amethyst shards. Geodes grow more. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.ECHO_SHARD, "Echo Shards", "Submit {count} echo shards from the deep dark.", "📡", 1, 3, 8.0D), "Submit Echo Shards", "Submit {count} echo shards from the deep dark. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.ANGLER_POTTERY_SHERD, "Angler Sherd", "🏺"), "Submit Angler Sherd", "Submit one angler sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_13, "Music Disc 13", "💿", 20.0D), "Submit Music Disc 13", "Submit one Music Disc 13. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_CAT, "Music Disc Cat", "🐈", 20.0D), "Submit Music Disc Cat", "Submit one Music Disc Cat. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "Sentry Trim", "🏴", 18.0D), "Submit Sentry Trim", "Submit one sentry trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "Silence Trim", "🤫", 35.0D), "Submit Silence Trim", "Submit one silence trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.ARCHER_POTTERY_SHERD, "Archer Sherd", "🏹"), "Submit Archer Sherd", "Submit one archer sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.ARMS_UP_POTTERY_SHERD, "Arms Up Sherd", "🙌"), "Submit Arms Up Sherd", "Submit one arms up sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.BLADE_POTTERY_SHERD, "Blade Sherd", "🗡️"), "Submit Blade Sherd", "Submit one blade sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.BREWER_POTTERY_SHERD, "Brewer Sherd", "🧪"), "Submit Brewer Sherd", "Submit one brewer sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.BURN_POTTERY_SHERD, "Burn Sherd", "🔥"), "Submit Burn Sherd", "Submit one burn sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.DANGER_POTTERY_SHERD, "Danger Sherd", "⚠️"), "Submit Danger Sherd", "Submit one danger sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.EXPLORER_POTTERY_SHERD, "Explorer Sherd", "🧭"), "Submit Explorer Sherd", "Submit one explorer sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.FLOW_POTTERY_SHERD, "Flow Sherd", "🌊"), "Submit Flow Sherd", "Submit one flow sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.FRIEND_POTTERY_SHERD, "Friend Sherd", "🤝"), "Submit Friend Sherd", "Submit one friend sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.GUSTER_POTTERY_SHERD, "Guster Sherd", "🌬️"), "Submit Guster Sherd", "Submit one guster sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.HEART_POTTERY_SHERD, "Heart Sherd", "❤️"), "Submit Heart Sherd", "Submit one heart sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.HEARTBREAK_POTTERY_SHERD, "Heartbreak Sherd", "💔"), "Submit Heartbreak Sherd", "Submit one heartbreak sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.HOWL_POTTERY_SHERD, "Howl Sherd", "🐺"), "Submit Howl Sherd", "Submit one howl sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.MINER_POTTERY_SHERD, "Miner Sherd", "⛏️"), "Submit Miner Sherd", "Submit one miner sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.MOURNER_POTTERY_SHERD, "Mourner Sherd", "😢"), "Submit Mourner Sherd", "Submit one mourner sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.PLENTY_POTTERY_SHERD, "Plenty Sherd", "🌾"), "Submit Plenty Sherd", "Submit one plenty sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.PRIZE_POTTERY_SHERD, "Prize Sherd", "🏆"), "Submit Prize Sherd", "Submit one prize sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.SCRAPE_POTTERY_SHERD, "Scrape Sherd", "🖌️"), "Submit Scrape Sherd", "Submit one scrape sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.SHEAF_POTTERY_SHERD, "Sheaf Sherd", "🌾"), "Submit Sheaf Sherd", "Submit one sheaf sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.SHELTER_POTTERY_SHERD, "Shelter Sherd", "🏠"), "Submit Shelter Sherd", "Submit one shelter sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.SKULL_POTTERY_SHERD, "Skull Sherd", "💀"), "Submit Skull Sherd", "Submit one skull sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, sherd(Items.SNORT_POTTERY_SHERD, "Snort Sherd", "🐽"), "Submit Snort Sherd", "Submit one snort sherd. Put the next one on a decorated pot. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_BLOCKS, "Music Disc Blocks", "🧱", 20.0D), "Submit Music Disc Blocks", "Submit one Music Disc Blocks. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_BOUNCE, "Music Disc Bounce", "🏀", 20.0D), "Submit Music Disc Bounce", "Submit one Music Disc Bounce. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_CHIRP, "Music Disc Chirp", "🐦", 20.0D), "Submit Music Disc Chirp", "Submit one Music Disc Chirp. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_CREATOR, "Music Disc Creator", "🛠️", 26.0D), "Submit Music Disc Creator", "Submit one Music Disc Creator. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_CREATOR_MUSIC_BOX, "Creator Music Box", "🎶", 26.0D), "Submit Creator Music Box", "Submit one Creator Music Box. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_FAR, "Music Disc Far", "🏞️", 20.0D), "Submit Music Disc Far", "Submit one Music Disc Far. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_LAVA_CHICKEN, "Lava Chicken Disc", "🐔", 24.0D), "Submit Lava Chicken Disc", "Submit one Lava Chicken Disc. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_MALL, "Music Disc Mall", "🛍️", 20.0D), "Submit Music Disc Mall", "Submit one Music Disc Mall. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_MELLOHI, "Music Disc Mellohi", "🎼", 20.0D), "Submit Music Disc Mellohi", "Submit one Music Disc Mellohi. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_STAL, "Music Disc Stal", "🪨", 20.0D), "Submit Music Disc Stal", "Submit one Music Disc Stal. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_STRAD, "Music Disc Strad", "🎻", 20.0D), "Submit Music Disc Strad", "Submit one Music Disc Strad. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_WARD, "Music Disc Ward", "🛡️", 20.0D), "Submit Music Disc Ward", "Submit one Music Disc Ward. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_11, "Music Disc 11", "🔢", 20.0D), "Submit Music Disc 11", "Submit one Music Disc 11. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_WAIT, "Music Disc Wait", "⏳", 20.0D), "Submit Music Disc Wait", "Submit one Music Disc Wait. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_OTHERSIDE, "Music Disc Otherside", "🚪", 26.0D), "Submit Music Disc Otherside", "Submit one Music Disc Otherside. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_RELIC, "Music Disc Relic", "🏺", 28.0D), "Submit Music Disc Relic", "Submit one Music Disc Relic. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_5, "Music Disc 5", "5️⃣", 30.0D), "Submit Music Disc 5", "Submit one Music Disc 5. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, true, false, 8, disc(Items.MUSIC_DISC_PIGSTEP, "Music Disc Pigstep", "🐽", 32.0D), "Submit Music Disc Pigstep", "Submit one Music Disc Pigstep. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_PRECIPICE, "Music Disc Precipice", "⛰️", 28.0D), "Submit Music Disc Precipice", "Submit one Music Disc Precipice. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, disc(Items.MUSIC_DISC_TEARS, "Music Disc Tears", "😭", 26.0D), "Submit Music Disc Tears", "Submit one Music Disc Tears. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, "Dune Trim", "🏜️", 18.0D), "Submit Dune Trim", "Submit one dune trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "Coast Trim", "🌊", 18.0D), "Submit Coast Trim", "Submit one coast trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, "Wild Trim", "🌿", 20.0D), "Submit Wild Trim", "Submit one wild trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, "Ward Trim", "📡", 26.0D), "Submit Ward Trim", "Submit one ward trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, "Eye Trim", "👁️", 22.0D), "Submit Eye Trim", "Submit one eye trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, "Vex Trim", "🪽", 26.0D), "Submit Vex Trim", "Submit one vex trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, "Tide Trim", "🔱", 26.0D), "Submit Tide Trim", "Submit one tide trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, true, false, 8, trim(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, "Snout Trim", "🐽", 24.0D), "Submit Snout Trim", "Submit one snout trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, true, false, 8, trim(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, "Rib Trim", "🦴", 24.0D), "Submit Rib Trim", "Submit one rib trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, true, 10, trim(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "Spire Trim", "🏙️", 28.0D), "Submit Spire Trim", "Submit one spire trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, "Wayfinder Trim", "🧭", 28.0D), "Submit Wayfinder Trim", "Submit one wayfinder trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, "Shaper Trim", "🏺", 28.0D), "Submit Shaper Trim", "Submit one shaper trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, "Raiser Trim", "🙌", 28.0D), "Submit Raiser Trim", "Submit one raiser trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, "Host Trim", "🏠", 28.0D), "Submit Host Trim", "Submit one host trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, "Flow Trim", "🌬️", 24.0D), "Submit Flow Trim", "Submit one flow trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, trim(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, "Bolt Trim", "⚡", 24.0D), "Submit Bolt Trim", "Submit one bolt trim template. Copy it first if you want to keep the pattern. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.ZOMBIE_HEAD, "Zombie Heads", "Submit {count} zombie heads. Around here, every tenth zombie has one to spare.", "🧟", 1, 3, 8.0D), "Submit Zombie Heads", "Submit {count} zombie heads. Around here, every tenth zombie has one to spare. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.CREEPER_HEAD, "Creeper Heads", "Submit one creeper head. No charged creeper paperwork is required here.", "💥", 1, 1, 14.0D), "Submit Creeper Heads", "Submit one creeper head. No charged creeper paperwork is required here. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.PIGLIN_HEAD, "Piglin Heads", "Submit one piglin head. The Nether has lost property too.", "🐽", 1, 1, 14.0D), "Submit Piglin Heads", "Submit one piglin head. The Nether has lost property too. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.WITHER_SKELETON_SKULL, "Wither Skeleton Skulls", "Submit one wither skeleton skull. Keep two more if you have plans.", "💀", 1, 1, 20.0D), "Submit Wither Skeleton Skulls", "Submit one wither skeleton skull. Keep two more if you have plans. Hold the items in your inventory, then click Claim."),
            option(10, false, true, 10, new ItemSubmissionTask(Items.DRAGON_HEAD, "Dragon Heads", "Submit a dragon head. It is difficult to decorate with two anyway.", "🐉", 1, 1, 25.0D), "Submit Dragon Heads", "Submit a dragon head. It is difficult to decorate with two anyway. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.OCHRE_FROGLIGHT, "Ochre Froglights", "Submit {count} ochre froglights. Keep the next batch for a warm ceiling.", "🟡", 1, 3, 8.0D), "Submit Ochre Froglights", "Submit {count} ochre froglights. Keep the next batch for a warm ceiling. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.VERDANT_FROGLIGHT, "Verdant Froglights", "Submit {count} verdant froglights. They make excellent hidden lighting.", "🟢", 1, 3, 8.0D), "Submit Verdant Froglights", "Submit {count} verdant froglights. They make excellent hidden lighting. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.PEARLESCENT_FROGLIGHT, "Pearlescent Froglights", "Submit {count} pearlescent froglights. Try the next ones in a floor pattern.", "🟣", 1, 3, 8.0D), "Submit Pearlescent Froglights", "Submit {count} pearlescent froglights. Try the next ones in a floor pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.DANDELION, "Dandelions", "🌼"), "Submit Dandelions", "Submit {count} dandelions. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.POPPY, "Poppies", "🌹"), "Submit Poppies", "Submit {count} poppies. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.BLUE_ORCHID, "Blue Orchids", "🪻"), "Submit Blue Orchids", "Submit {count} blue orchids. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.ALLIUM, "Alliums", "🟣"), "Submit Alliums", "Submit {count} alliums. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.AZURE_BLUET, "Azure Bluets", "🌼"), "Submit Azure Bluets", "Submit {count} azure bluets. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.RED_TULIP, "Red Tulips", "🌷"), "Submit Red Tulips", "Submit {count} red tulips. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.ORANGE_TULIP, "Orange Tulips", "🌷"), "Submit Orange Tulips", "Submit {count} orange tulips. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.WHITE_TULIP, "White Tulips", "🌷"), "Submit White Tulips", "Submit {count} white tulips. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.PINK_TULIP, "Pink Tulips", "🌷"), "Submit Pink Tulips", "Submit {count} pink tulips. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.OXEYE_DAISY, "Oxeye Daisies", "🌼"), "Submit Oxeye Daisies", "Submit {count} oxeye daisies. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.CORNFLOWER, "Cornflowers", "🪻"), "Submit Cornflowers", "Submit {count} cornflowers. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.LILY_OF_THE_VALLEY, "Lilies of the Valley", "🤍"), "Submit Lilies of the Valley", "Submit {count} lilies of the valley. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.LILAC, "Lilacs", "🪻"), "Submit Lilacs", "Submit {count} lilacs. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.ROSE_BUSH, "Rose Bushes", "🌹"), "Submit Rose Bushes", "Submit {count} rose bushes. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.PEONY, "Peonies", "🌸"), "Submit Peonies", "Submit {count} peonies. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.WILDFLOWERS, "Wildflowers", "💐"), "Submit Wildflowers", "Submit {count} wildflowers. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.PINK_PETALS, "Pink Petals", "🌸"), "Submit Pink Petals", "Submit {count} pink petals. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, flower(Items.CACTUS_FLOWER, "Cactus Flowers", "🌵"), "Submit Cactus Flowers", "Submit {count} cactus flowers. Keep a few and add some colour to a build. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.SPORE_BLOSSOM, "Spore Blossoms", "Submit {count} spore blossoms. Give the next lush cave a ceiling garden.", "🌺", 2, 5, 3.0D), "Submit Spore Blossoms", "Submit {count} spore blossoms. Give the next lush cave a ceiling garden. Hold the items in your inventory, then click Claim."),
            option(1, true, false, 8, new ItemSubmissionTask(Items.WITHER_ROSE, "Wither Roses", "Submit one wither rose. This bouquet bites back.", "🥀", 1, 1, 18.0D), "Submit Wither Roses", "Submit one wither rose. This bouquet bites back. Hold the items in your inventory, then click Claim."),
            option(1, false, true, 10, new ItemSubmissionTask(Items.CHORUS_FLOWER, "Chorus Flowers", "Submit {count} chorus flowers. They have travelled far enough.", "🟪", 2, 5, 3.0D), "Submit Chorus Flowers", "Submit {count} chorus flowers. They have travelled far enough. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.TORCHFLOWER, "Torchflowers", "Submit {count} torchflowers. Let the next ancient seed brighten your garden.", "🏵️", 1, 3, 6.0D), "Submit Torchflowers", "Submit {count} torchflowers. Let the next ancient seed brighten your garden. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.PITCHER_PLANT, "Pitcher Plants", "Submit {count} pitcher plants. The sniffer approves of the landscaping.", "🪻", 1, 3, 6.0D), "Submit Pitcher Plants", "Submit {count} pitcher plants. The sniffer approves of the landscaping. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.OPEN_EYEBLOSSOM, "Open Eyeblossoms", "Submit {count} open eyeblossoms while they are watching.", "👁️", 3, 8, 2.0D), "Submit Open Eyeblossoms", "Submit {count} open eyeblossoms while they are watching. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.CLOSED_EYEBLOSSOM, "Closed Eyeblossoms", "Submit {count} closed eyeblossoms. Do not wake them.", "😴", 3, 8, 2.0D), "Submit Closed Eyeblossoms", "Submit {count} closed eyeblossoms. Do not wake them. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.GOLDEN_DANDELION, "Golden Dandelions", "Submit one golden dandelion. Ordinary yellow was not enough.", "🌟", 1, 1, 15.0D), "Submit Golden Dandelions", "Submit one golden dandelion. Ordinary yellow was not enough. Hold the items in your inventory, then click Claim."),

            // Unusual building materials and natural finds.
            option(10, false, false, 5, new ItemSubmissionTask(Items.AMETHYST_CLUSTER, "Amethyst Clusters", "Submit {count} amethyst clusters. Silk Touch keeps the sparkle intact.", "🔮", 1, 2, 7.0D), "Submit Amethyst Clusters", "Submit {count} amethyst clusters. Silk Touch keeps the sparkle intact. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.ARMADILLO_SCUTE, "Armadillo Scutes", "🛡️", 4, 10, 1.5D), "Submit Armadillo Scutes", "Submit {count} armadillo scutes. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.AXOLOTL_BUCKET, "Buckets of Axolotl", "Submit one bucket of axolotl. It is a passenger, not cargo.", "🪣", 1, 1, 14.0D), "Submit Buckets of Axolotl", "Submit one bucket of axolotl. It is a passenger, not cargo. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.AZALEA, "Azaleas", "🌿", 4, 12, 1.0D), "Submit Azaleas", "Submit {count} azaleas. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.FLOWERING_AZALEA, "Flowering Azaleas", "🌺", 4, 12, 1.25D), "Submit Flowering Azaleas", "Submit {count} flowering azaleas. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.AZALEA_LEAVES, "Azalea Leaves", "🍃", 8, 24, 0.6D), "Submit Azalea Leaves", "Submit {count} azalea leaves. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.FLOWERING_AZALEA_LEAVES, "Flowering Azalea Leaves", "🌸", 8, 24, 0.75D), "Submit Flowering Azalea Leaves", "Submit {count} flowering azalea leaves. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.BLUE_EGG, "Blue Eggs", "Submit {count} blue eggs. The shell is doing most of the work here.", "🔵", 2, 4, 3.0D), "Submit Blue Eggs", "Submit {count} blue eggs. The shell is doing most of the work here. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.BROWN_EGG, "Brown Eggs", "Submit {count} brown eggs. Breakfast has biome variants now.", "🟤", 2, 4, 3.0D), "Submit Brown Eggs", "Submit {count} brown eggs. Breakfast has biome variants now. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.BLUE_ICE, "Blue Ice", "🧊", 8, 24, 0.75D), "Submit Blue Ice", "Submit {count} blue ice. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.TUBE_CORAL_BLOCK, "Tube Coral Blocks", "Submit {count} living tube coral blocks. Keep them wet.", "🪸", 2, 6, 2.0D), "Submit Tube Coral Blocks", "Submit {count} living tube coral blocks. Keep them wet. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.BRAIN_CORAL_FAN, "Brain Coral Fans", "Submit {count} living brain coral fans. The reef can spare a small sample.", "🧠", 2, 6, 2.0D), "Submit Brain Coral Fans", "Submit {count} living brain coral fans. The reef can spare a small sample. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.BUBBLE_CORAL_BLOCK, "Bubble Coral Blocks", "Submit {count} living bubble coral blocks. No popping them.", "🫧", 2, 6, 2.0D), "Submit Bubble Coral Blocks", "Submit {count} living bubble coral blocks. No popping them. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.FIRE_CORAL_FAN, "Fire Coral Fans", "Submit {count} living fire coral fans. They are not actually on fire.", "🔥", 2, 6, 2.0D), "Submit Fire Coral Fans", "Submit {count} living fire coral fans. They are not actually on fire. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.HORN_CORAL_BLOCK, "Horn Coral Blocks", "Submit {count} living horn coral blocks from a warm reef.", "📯", 2, 6, 2.0D), "Submit Horn Coral Blocks", "Submit {count} living horn coral blocks from a warm reef. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.CALCITE, "Calcite", "Submit {count} calcite. Geodes have excellent interior walls.", "⬜", 16, 48, 0.35D), "Submit Calcite", "Submit {count} calcite. Geodes have excellent interior walls. Hold the items in your inventory, then click Claim."),
            option(6, false, false, 5, new ItemSubmissionTask(Items.CUT_COPPER_STAIRS.waxed().oxidized(), "Waxed Oxidized Cut Copper Stairs", "Submit {count} waxed oxidized cut copper stairs. The colour was worth the wait.", "🟦", 2, 6, 3.0D), "Submit Waxed Oxidized Cut Copper Stairs", "Submit {count} waxed oxidized cut copper stairs. The colour was worth the wait. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.CHISELED_RED_SANDSTONE, "Chiseled Red Sandstone", "🧱", 8, 24, 0.6D), "Submit Chiseled Red Sandstone", "Submit {count} chiseled red sandstone. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.CHISELED_RESIN_BRICKS, "Chiseled Resin Bricks", "Submit {count} chiseled resin bricks. Pale gardens can be colourful after all.", "🟠", 4, 12, 1.5D), "Submit Chiseled Resin Bricks", "Submit {count} chiseled resin bricks. Pale gardens can be colourful after all. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.CHISELED_SULFUR, "Chiseled Sulfur", "Submit {count} chiseled sulfur. Architecture should have a smell.", "🟡", 4, 12, 1.5D), "Submit Chiseled Sulfur", "Submit {count} chiseled sulfur. Architecture should have a smell. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.COARSE_DIRT, "Coarse Dirt", "🟫", 16, 48, 0.3D), "Submit Coarse Dirt", "Submit {count} coarse dirt. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.COPPER_HOE, "Copper Hoes", "Submit {count} copper hoes. Give the new tool tier a field test first.", "🟠", 1, 3, 4.0D), "Submit Copper Hoes", "Submit {count} copper hoes. Give the new tool tier a field test first. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.COPPER_TORCH, "Copper Torches", "Submit {count} copper torches. Green fire deserves better lighting design.", "🟢", 8, 24, 0.6D), "Submit Copper Torches", "Submit {count} copper torches. Green fire deserves better lighting design. Hold the items in your inventory, then click Claim."),
            option(6, false, false, 5, new ItemSubmissionTask(Items.CREAKING_HEART, "Creaking Hearts", "Submit {count} creaking hearts. The forest will notice.", "🫀", 1, 2, 12.0D), "Submit Creaking Hearts", "Submit {count} creaking hearts. The forest will notice. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().unaffected(), "Copper Golem Statues", "Submit {count} copper golem statues. Let one pose before it goes.", "🗿", 1, 3, 6.0D), "Submit Copper Golem Statues", "Submit {count} copper golem statues. Let one pose before it goes. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().exposed(), "Exposed Copper Golem Statues", "🗿", 1, 3, 7.0D), "Submit Exposed Copper Golem Statues", "Submit {count} exposed copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().weathered(), "Weathered Copper Golem Statues", "🗿", 1, 3, 8.0D), "Submit Weathered Copper Golem Statues", "Submit {count} weathered copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.weathering().oxidized(), "Oxidized Copper Golem Statues", "🗿", 1, 3, 9.0D), "Submit Oxidized Copper Golem Statues", "Submit {count} oxidized copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().unaffected(), "Waxed Copper Golem Statues", "🗿", 1, 3, 7.0D), "Submit Waxed Copper Golem Statues", "Submit {count} waxed copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().exposed(), "Waxed Exposed Copper Golem Statues", "🗿", 1, 3, 8.0D), "Submit Waxed Exposed Copper Golem Statues", "Submit {count} waxed exposed copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().weathered(), "Waxed Weathered Copper Golem Statues", "🗿", 1, 3, 9.0D), "Submit Waxed Weathered Copper Golem Statues", "Submit {count} waxed weathered copper golem statues. Hold the items in your inventory, then click Claim."),
            option(2, false, false, 5, new ItemSubmissionTask(Items.COPPER_GOLEM_STATUE.waxed().oxidized(), "Waxed Oxidized Copper Golem Statues", "🗿", 1, 3, 10.0D), "Submit Waxed Oxidized Copper Golem Statues", "Submit {count} waxed oxidized copper golem statues. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.DEEPSLATE_COAL_ORE, "Deepslate Coal Ore", "Submit {count} deepslate coal ore. Silk Touch a geological souvenir.", "⬛", 2, 6, 2.0D), "Submit Deepslate Coal Ore", "Submit {count} deepslate coal ore. Silk Touch a geological souvenir. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.DEEPSLATE_IRON_ORE, "Deepslate Iron Ore", "⛏️", 2, 6, 2.0D), "Submit Deepslate Iron Ore", "Submit {count} deepslate iron ore. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.DEEPSLATE_GOLD_ORE, "Deepslate Gold Ore", "🟨", 2, 6, 2.5D), "Submit Deepslate Gold Ore", "Submit {count} deepslate gold ore. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.DEEPSLATE_LAPIS_ORE, "Deepslate Lapis Ore", "🔵", 2, 6, 2.5D), "Submit Deepslate Lapis Ore", "Submit {count} deepslate lapis ore. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.DEEPSLATE_DIAMOND_ORE, "Deepslate Diamond Ore", "💎", 1, 3, 7.0D), "Submit Deepslate Diamond Ore", "Submit {count} deepslate diamond ore. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.DIORITE_WALL, "Diorite Walls", "🧱", 16, 48, 0.3D), "Submit Diorite Walls", "Submit {count} diorite walls. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.ANDESITE_STAIRS, "Andesite Stairs", "🪨", 16, 48, 0.3D), "Submit Andesite Stairs", "Submit {count} andesite stairs. Hold the items in your inventory, then click Claim."),
            option(80, false, false, 5, new ItemSubmissionTask(Items.EMERALD, "Emeralds", "Convert {count} emeralds into dabloons. Villagers need not know.", "💚", 16, 48, 0.5D), "Submit Emeralds", "Convert {count} emeralds into dabloons. Villagers need not know. Hold the items in your inventory, then click Claim."),
            option(80, false, false, 5, new ItemSubmissionTask(Items.EMERALD_BLOCK, "Emerald Blocks", "Convert {count} emerald blocks into dabloons. This is the compact exchange counter.", "🟩", 2, 6, 5.0D), "Submit Emerald Blocks", "Convert {count} emerald blocks into dabloons. This is the compact exchange counter. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.GLOW_LICHEN, "Glow Lichen", "✨", 16, 48, 0.4D), "Submit Glow Lichen", "Submit {count} glow lichen. Hold the items in your inventory, then click Claim."),
            option(12, true, false, 8, new ItemSubmissionTask(Items.GLOWSTONE, "Glowstone", "Submit {count} glowstone. The ceiling did not need all of it.", "🌟", 8, 24, 1.0D), "Submit Glowstone", "Submit {count} glowstone. The ceiling did not need all of it. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.HANGING_ROOTS, "Hanging Roots", "🌱", 8, 24, 0.75D), "Submit Hanging Roots", "Submit {count} hanging roots. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.JACK_O_LANTERN, "Jack o'Lanterns", "Submit {count} jack o'lanterns. Keep one outside for atmosphere.", "🎃", 4, 12, 1.5D), "Submit Jack o'Lanterns", "Submit {count} jack o'lanterns. Keep one outside for atmosphere. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, "Light Weighted Pressure Plates", "Submit {count} light weighted pressure plates. Gold can do redstone too.", "🟨", 2, 6, 2.0D), "Submit Light Weighted Pressure Plates", "Submit {count} light weighted pressure plates. Gold can do redstone too. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.MANGROVE_CHEST_BOAT, "Mangrove Chest Boats", "Submit {count} mangrove chest boats. Storage has never been so seaworthy.", "🛶", 1, 2, 6.0D), "Submit Mangrove Chest Boats", "Submit {count} mangrove chest boats. Storage has never been so seaworthy. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.MANGROVE_PROPAGULE, "Mangrove Propagules", "🌱", 8, 24, 0.6D), "Submit Mangrove Propagules", "Submit {count} mangrove propagules. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.MUSHROOM_STEM, "Mushroom Stems", "Submit {count} mushroom stems. Giant mushrooms have excellent beams.", "🍄", 8, 24, 0.75D), "Submit Mushroom Stems", "Submit {count} mushroom stems. Giant mushrooms have excellent beams. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.POTENT_SULFUR, "Potent Sulfur", "Submit {count} potent sulfur. Handle the concentrated stuff carefully.", "⚗️", 2, 6, 3.0D), "Submit Potent Sulfur", "Submit {count} potent sulfur. Handle the concentrated stuff carefully. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.PRISMARINE, "Prismarine", "Submit {count} prismarine. Monument green works outside the ocean too.", "🌊", 8, 24, 0.75D), "Submit Prismarine", "Submit {count} prismarine. Monument green works outside the ocean too. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.PRISMARINE_SHARD, "Prismarine Shards", "🔱", 12, 32, 0.5D), "Submit Prismarine Shards", "Submit {count} prismarine shards. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.PRISMARINE_CRYSTALS, "Prismarine Crystals", "💠", 8, 24, 0.75D), "Submit Prismarine Crystals", "Submit {count} prismarine crystals. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.PUFFERFISH, "Pufferfish", "Submit {count} pufferfish. Please do not make lunch with them.", "🐡", 2, 8, 1.5D), "Submit Pufferfish", "Submit {count} pufferfish. Please do not make lunch with them. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.RAW_GOLD_BLOCK, "Blocks of Raw Gold", "🟨", 2, 6, 4.0D), "Submit Blocks of Raw Gold", "Submit {count} blocks of raw gold. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.RAW_IRON_BLOCK, "Blocks of Raw Iron", "⬜", 2, 6, 3.0D), "Submit Blocks of Raw Iron", "Submit {count} blocks of raw iron. Hold the items in your inventory, then click Claim."),
            option(20, false, false, 5, new ItemSubmissionTask(Items.DIAMOND_BLOCK, "Diamond Blocks", "Convert {count} diamond blocks into dabloons. There is no raw diamond block, so polished wealth will do.", "💎", 1, 3, 10.0D), "Submit Diamond Blocks", "Convert {count} diamond blocks into dabloons. There is no raw diamond block, so polished wealth will do. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.SLIME_BLOCK, "Slime Blocks", "Submit {count} slime blocks. Bounce on them before packing them up.", "🟩", 2, 8, 2.0D), "Submit Slime Blocks", "Submit {count} slime blocks. Bounce on them before packing them up. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.SLIME_BALL, "Slimeballs", "🟢", 12, 32, 0.5D), "Submit Slimeballs", "Submit {count} slimeballs. Hold the items in your inventory, then click Claim."),
            option(10, true, false, 8, new ItemSubmissionTask(Items.SPECTRAL_ARROW, "Spectral Arrows", "Submit {count} spectral arrows. Everything looks better with an outline.", "🏹", 8, 24, 0.75D), "Submit Spectral Arrows", "Submit {count} spectral arrows. Everything looks better with an outline. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.SPONGE, "Sponges", "Submit {count} dry sponges. Ocean monuments have unusual cleaning cupboards.", "🧽", 1, 4, 5.0D), "Submit Sponges", "Submit {count} dry sponges. Ocean monuments have unusual cleaning cupboards. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.WET_SPONGE, "Wet Sponges", "Submit {count} wet sponges. The furnace can have the next batch.", "💧", 1, 4, 5.0D), "Submit Wet Sponges", "Submit {count} wet sponges. The furnace can have the next batch. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.SPRUCE_TRAPDOOR, "Spruce Trapdoors", "Submit {count} spruce trapdoors. Builders know these are wall panels.", "🪵", 8, 24, 0.6D), "Submit Spruce Trapdoors", "Submit {count} spruce trapdoors. Builders know these are wall panels. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.SUSPICIOUS_SAND, "Suspicious Sand", "Submit one suspicious sand block. Try not to shake the evidence.", "🏜️", 1, 1, 12.0D), "Submit Suspicious Sand", "Submit one suspicious sand block. Try not to shake the evidence. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.SUSPICIOUS_GRAVEL, "Suspicious Gravel", "Submit one suspicious gravel block. Suspicion is heavier than it looks.", "🪨", 1, 1, 12.0D), "Submit Suspicious Gravel", "Submit one suspicious gravel block. Suspicion is heavier than it looks. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.TADPOLE_BUCKET, "Buckets of Tadpole", "Submit one bucket of tadpole. Small frog, large travel plans.", "🪣", 1, 1, 12.0D), "Submit Buckets of Tadpole", "Submit one bucket of tadpole. Small frog, large travel plans. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.TNT_MINECART, "Minecarts with TNT", "Submit {count} minecarts with TNT. No test drive is necessary.", "💣", 1, 3, 5.0D), "Submit Minecarts with TNT", "Submit {count} minecarts with TNT. No test drive is necessary. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.FURNACE_MINECART, "Minecarts with Furnaces", "Submit {count} minecarts with furnaces. Powered rail is not the only answer.", "🚂", 1, 3, 5.0D), "Submit Minecarts with Furnaces", "Submit {count} minecarts with furnaces. Powered rail is not the only answer. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.TURTLE_HELMET, "Turtle Shells", "Submit one turtle shell. Ten extra seconds underwater were nice.", "🐢", 1, 1, 18.0D), "Submit Turtle Shells", "Submit one turtle shell. Ten extra seconds underwater were nice. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.VINE, "Vines", "🌿", 12, 32, 0.5D), "Submit Vines", "Submit {count} vines. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.WRITTEN_BOOK, "Written Books", "Submit one written book. Give it a title worth shelving.", "📖", 1, 1, 12.0D), "Submit Written Books", "Submit one written book. Give it a title worth shelving. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.PLAYER_HEAD, "Player Heads", "Submit one player head. The likeness is uncanny.", "🗿", 1, 1, 15.0D), "Submit Player Heads", "Submit one player head. The likeness is uncanny. Hold the items in your inventory, then click Claim."),
            option(12, false, false, 5, new ItemSubmissionTask(Items.EXPERIENCE_BOTTLE, "Bottles o' Enchanting", "Submit {count} bottles o' enchanting. Experience is liquid currency now.", "✨", 4, 12, 1.5D), "Submit Bottles o' Enchanting", "Submit {count} bottles o' enchanting. Experience is liquid currency now. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.FIREFLY_BUSH, "Firefly Bushes", "Submit {count} firefly bushes. Save the next patch for a glowing garden.", "✨", 4, 12, 1.25D), "Submit Firefly Bushes", "Submit {count} firefly bushes. Save the next patch for a glowing garden. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, new ItemSubmissionTask(Items.GOAT_HORN, "Goat Horns", "Submit one goat horn. Sound it once before it leaves.", "📯", 1, 1, 12.0D), "Submit Goat Horns", "Submit one goat horn. Sound it once before it leaves. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, new ItemSubmissionTask(Items.NAUTILUS_SHELL, "Nautilus Shells", "Submit {count} nautilus shells. A conduit is only eight shells away.", "🐚", 2, 6, 3.0D), "Submit Nautilus Shells", "Submit {count} nautilus shells. A conduit is only eight shells away. Hold the items in your inventory, then click Claim."),
            option(5, false, false, 5, new ItemSubmissionTask(Items.SNIFFER_EGG, "Sniffer Eggs", "Submit one sniffer egg. The ancient seed detective can hatch next time.", "🥚", 1, 1, 18.0D), "Submit Sniffer Eggs", "Submit one sniffer egg. The ancient seed detective can hatch next time. Hold the items in your inventory, then click Claim."),
            option(6, false, false, 5, new ItemSubmissionTask(Items.OMINOUS_BOTTLE, "Ominous Bottles", "Submit {count} ominous bottles. Keep the bad decisions corked.", "🍾", 1, 3, 7.0D), "Submit Ominous Bottles", "Submit {count} ominous bottles. Keep the bad decisions corked. Hold the items in your inventory, then click Claim."),
            option(5, false, false, 5, new ItemSubmissionTask(Items.HEART_OF_THE_SEA, "Hearts of the Sea", "Submit one heart of the sea. The ocean keeps strange treasure.", "💙", 1, 1, 18.0D), "Submit Hearts of the Sea", "Submit one heart of the sea. The ocean keeps strange treasure. Hold the items in your inventory, then click Claim."),
            option(5, false, false, 5, new ItemSubmissionTask(Items.SEA_LANTERN, "Sea Lanterns", "Submit {count} sea lanterns. The ocean keeps strange treasure, and it brought lighting.", "??", 1, 3, 6.0D), "Submit Sea Lanterns", "Submit {count} sea lanterns. The ocean keeps strange treasure, and it brought lighting. Hold the items in your inventory, then click Claim."),

            option(4, false, false, 5, new ItemSubmissionTask(Items.TUBE_CORAL, "Tube Coral", "Submit {count} tube coral. Keep it wet.", "\uD83E\uDEB8", 2, 6, 2.0D), "Submit Tube Coral", "Submit {count} tube coral. Keep it wet. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.BRAIN_CORAL, "Brain Coral", "Submit {count} brain coral. It is a little easier to collect than to use for decisions.", "\uD83E\uDDE0", 2, 6, 2.0D), "Submit Brain Coral", "Submit {count} brain coral. It is a little easier to collect than to use for decisions. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, new ItemSubmissionTask(Items.MYCELIUM, "Mycelium", "Submit {count} mycelium.", "\uD83C\uDF44", 8, 24, 0.75D), "Submit Mycelium", "Submit {count} mycelium. Hold the items in your inventory, then click Claim."),

            // Banner patterns are collectors' targets, not a reason to see banners every week.
            option(1, false, false, 5, new ItemSubmissionTask(Items.FLOWER_BANNER_PATTERN, "Flower Banner Pattern", "🌼", 1, 1, 12.0D), "Submit Flower Banner Pattern", "Submit {count} flower banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.CREEPER_BANNER_PATTERN, "Creeper Banner Pattern", "💥", 1, 1, 14.0D), "Submit Creeper Banner Pattern", "Submit {count} creeper banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.SKULL_BANNER_PATTERN, "Skull Banner Pattern", "💀", 1, 1, 16.0D), "Submit Skull Banner Pattern", "Submit {count} skull banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.MOJANG_BANNER_PATTERN, "Thing Banner Pattern", "🍎", 1, 1, 18.0D), "Submit Thing Banner Pattern", "Submit {count} thing banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.GLOBE_BANNER_PATTERN, "Globe Banner Pattern", "🌍", 1, 1, 14.0D), "Submit Globe Banner Pattern", "Submit {count} globe banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, true, false, 8, new ItemSubmissionTask(Items.PIGLIN_BANNER_PATTERN, "Snout Banner Pattern", "🐽", 1, 1, 16.0D), "Submit Snout Banner Pattern", "Submit {count} snout banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.FLOW_BANNER_PATTERN, "Flow Banner Pattern", "🌊", 1, 1, 18.0D), "Submit Flow Banner Pattern", "Submit {count} flow banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.GUSTER_BANNER_PATTERN, "Guster Banner Pattern", "🌬️", 1, 1, 18.0D), "Submit Guster Banner Pattern", "Submit {count} guster banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.FIELD_MASONED_BANNER_PATTERN, "Field Masoned Banner Pattern", "🧱", 1, 1, 14.0D), "Submit Field Masoned Banner Pattern", "Submit {count} field masoned banner pattern. Hold the items in your inventory, then click Claim."),
            option(1, false, false, 5, new ItemSubmissionTask(Items.BORDURE_INDENTED_BANNER_PATTERN, "Bordure Indented Banner Pattern", "🚩", 1, 1, 14.0D), "Submit Bordure Indented Banner Pattern", "Submit {count} bordure indented banner pattern. Hold the items in your inventory, then click Claim."),

            // Custom items use exact component matching. Filled or modified items cannot be consumed by mistake.
            option(10, false, false, 5, custom("1-leaf-clover", "One-Leaf Clovers", "Submit {count} one-leaf clovers. Luck has to start somewhere.", "☘️", 4, 12, 1.0D), "Submit One-Leaf Clovers", "Submit {count} one-leaf clovers. Luck has to start somewhere. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, custom("2-leaf-clover", "Two-Leaf Clovers", "Submit {count} two-leaf clovers. Twice the leaves, perhaps twice the luck.", "☘️", 2, 8, 2.0D), "Submit Two-Leaf Clovers", "Submit {count} two-leaf clovers. Twice the leaves, perhaps twice the luck. Hold the items in your inventory, then click Claim."),
            option(6, false, false, 5, custom("3-leaf-clover", "Three-Leaf Clovers", "Submit {count} three-leaf clovers. Almost famously lucky.", "☘️", 1, 4, 4.0D), "Submit Three-Leaf Clovers", "Submit {count} three-leaf clovers. Almost famously lucky. Hold the items in your inventory, then click Claim."),
            option(3, false, false, 5, custom("4-leaf-clover", "Four-Leaf Clovers", "Submit one four-leaf clover. Spend the luck while it lasts.", "🍀", 1, 1, 18.0D), "Submit Four-Leaf Clovers", "Submit one four-leaf clover. Spend the luck while it lasts. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("beer", "Beer", "Submit three beers. One brewing batch should cover quality control.", "🍺", 3, 3, 4.0D), "Submit Beer", "Submit three beers. One brewing batch should cover quality control. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("golden-nutritional-paste", "Golden Nutritional Paste", "Submit one portion of golden nutritional paste. Nine ingredients later, it is food, technically.", "🟨", 1, 1, 14.0D), "Submit Golden Nutritional Paste", "Submit one portion of golden nutritional paste. Nine ingredients later, it is food, technically. Hold the items in your inventory, then click Claim."),
            option(8, false, false, 5, custom("soul", "Souls", "Submit {count} souls. Do not ask where the collection box goes.", "👻", 2, 6, 3.0D), "Submit Souls", "Submit {count} souls. Do not ask where the collection box goes. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("sushi", "Sushi", "Submit {count} pieces of sushi. Freshly caught is best.", "🍣", 2, 8, 2.0D), "Submit Sushi", "Submit {count} pieces of sushi. Freshly caught is best. Hold the items in your inventory, then click Claim."),
            option(6, false, false, 5, custom("charm-sculk-phial", "Empty Sculk Phials", "Submit one empty sculk phial. Phials that contain experience do not count.", "🧪", 1, 1, 30.0D), "Submit Empty Sculk Phials", "Submit one empty sculk phial. Phials that contain experience do not count. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, custom("disc-9am", "9AM Disc", "Submit one 9AM disc. Give it one last spin first.", "🌅", 1, 1, 22.0D), "Submit 9AM Disc", "Submit one 9AM disc. Give it one last spin first. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, custom("disc-death", "Death Disc", "Submit one Death disc. The title is not an instruction.", "💀", 1, 1, 24.0D), "Submit Death Disc", "Submit one Death disc. The title is not an instruction. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, custom("disc-dog", "Dog Disc", "Submit one Dog disc. The jukebox will miss it.", "🐕", 1, 1, 22.0D), "Submit Dog Disc", "Submit one Dog disc. The jukebox will miss it. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, custom("disc-droopy-likes-ricochet", "Droopy Likes Ricochet Disc", "Submit one Droopy Likes Ricochet disc. A long title deserves one last play.", "💿", 1, 1, 24.0D), "Submit Droopy Likes Ricochet Disc", "Submit one Droopy Likes Ricochet disc. A long title deserves one last play. Hold the items in your inventory, then click Claim."),
            option(4, false, false, 5, custom("disc-droopy-likes-your-face", "Droopy Likes Your Face Disc", "Submit one Droopy Likes Your Face disc. Droopy has excellent taste.", "💿", 1, 1, 24.0D), "Submit Droopy Likes Your Face Disc", "Submit one Droopy Likes Your Face disc. Droopy has excellent taste. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("cookie-jar", "Cookie Jars", "Submit one cookie jar. Keep the next one on the kitchen counter.", "🍪", 1, 1, 12.0D), "Submit Cookie Jars", "Submit one cookie jar. Keep the next one on the kitchen counter. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("firefly-jar", "Firefly Jars", "Submit one firefly jar. Warm light needs no redstone.", "✨", 1, 1, 12.0D), "Submit Firefly Jars", "Submit one firefly jar. Warm light needs no redstone. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("fruit-bowl", "Fruit Bowls", "Submit one fruit bowl. Empty tables are a design choice, but not a good one.", "🍎", 1, 1, 12.0D), "Submit Fruit Bowls", "Submit one fruit bowl. Empty tables are a design choice, but not a good one. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("kettle", "Kettles", "Submit one kettle. Every build deserves a tea break.", "🫖", 1, 1, 12.0D), "Submit Kettles", "Submit one kettle. Every build deserves a tea break. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("spoons-carpet-grandiloquent", "Grandiloquent Spoons Carpets", "Submit one grandiloquent spoons carpet. Subtlety was never the point.", "🥄", 1, 1, 12.0D), "Submit Grandiloquent Spoons Carpets", "Submit one grandiloquent spoons carpet. Subtlety was never the point. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("spoons-carpet-junoesque", "Junoesque Spoons Carpets", "Submit one junoesque spoons carpet. The floor has standards.", "🥄", 1, 1, 12.0D), "Submit Junoesque Spoons Carpets", "Submit one junoesque spoons carpet. The floor has standards. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("spoons-carpet-meretricious", "Meretricious Spoons Carpets", "Submit one meretricious spoons carpet. Taste is subjective.", "🥄", 1, 1, 12.0D), "Submit Meretricious Spoons Carpets", "Submit one meretricious spoons carpet. Taste is subjective. Hold the items in your inventory, then click Claim."),
            option(10, false, false, 5, custom("vinyl-player", "Vinyl Players", "Submit one vinyl player. Its music disc makes this more than a furniture order.", "📻", 1, 1, 30.0D), "Submit Vinyl Players", "Submit one vinyl player. Its music disc makes this more than a furniture order. Hold the items in your inventory, then click Claim."),

            // Food
            option(2, false, false, 5, new EatItemTask(Items.BREAD, "Bread", "🍞", 5, 10, 0.75D), "Eat Bread", "Eat {count} bread."),
            option(5, false, false, 5, new EatItemTask(Items.BEETROOT, "Beetroot", "🫜", 8, 16, 0.5D), "Eat Beetroot", "Eat {count} beetroot."),
            option(3, false, false, 5, new EatItemTask(Items.COOKIE, "Cookies", "🍪", 6, 12, 0.6D), "Eat Cookies", "Eat {count} cookies."),
            option(2, false, false, 5, new EatItemTask(Items.BAKED_POTATO, "Baked Potatoes", "🥔", 5, 10, 0.75D), "Eat Baked Potatoes", "Eat {count} baked potatoes."),
            option(2, false, true, 10, new EatItemTask(Items.CHORUS_FRUIT, "Chorus Fruit", "🟣", 3, 7, 1.5D), "Eat Chorus Fruit", "Eat {count} chorus fruit."),
            option(2, false, false, 5, new EatItemTask(Items.CAKE, "Cake Slices", "🎂", 2, 6, 1.0D), "Eat Cake Slices", "Eat {count} cake slices."),
            option(10, false, false, 5, new EatItemTask(Items.SUSPICIOUS_STEW, "Suspicious Stew", "🥣", 1, 5, 6.0D), "Eat Suspicious Stew", "Eat {count} suspicious stew."),
            option(4, false, false, 5, new EatItemTask(Items.RABBIT_STEW, "Rabbit Stew", "🐇", 1, 2, 4.0D), "Eat Rabbit Stew", "Eat {count} rabbit stew."),
            option(4, false, false, 5, new EatItemTask(Items.PUMPKIN_PIE, "Pumpkin Pie", "🥧", 2, 4, 2.0D), "Eat Pumpkin Pie", "Eat {count} pumpkin pie."),
            option(4, false, false, 5, new EatItemTask(Items.BEETROOT_SOUP, "Beetroot Soup", "🥣", 1, 3, 2.5D), "Eat Beetroot Soup", "Eat {count} beetroot soup."),
            option(4, false, false, 5, new EatItemTask(Items.HONEY_BOTTLE, "Honey Bottles", "🍯", 1, 3, 3.0D), "Eat Honey Bottles", "Eat {count} honey bottles."),
            option(2, false, false, 5, new EatItemTask(Items.MUSHROOM_STEW, "Mushroom Stew", "🍄", 1, 3, 2.5D), "Eat Mushroom Stew", "Eat {count} mushroom stew."),
            option(2, false, false, 5, new EatItemTask(Items.DRIED_KELP, "Dried Kelp", "🌿", 8, 16, 0.5D), "Eat Dried Kelp", "Eat {count} dried kelp."),
            option(4, false, false, 5, new EatItemTask(Items.PUFFERFISH, "Pufferfish", "🐡", 1, 1, 6.0D), "Eat Pufferfish", "Eat {count} pufferfish."),
            option(4, false, false, 5, new EatItemTask(Items.TROPICAL_FISH, "Tropical Fish", "🐠", 1, 2, 4.0D), "Eat Tropical Fish", "Eat {count} tropical fish."),
            option(4, false, false, 5, new EatItemTask(Items.POISONOUS_POTATO, "Poisonous Potatoes", "🥔", 1, 5, 5.0D), "Eat Poisonous Potatoes", "Eat {count} poisonous potatoes."),

            // Enchanting
            option(3, false, false, 5, new EnchantAtTableTask(), "Arcane Appointments", "Enchant {count} items at an enchanting table."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.SWORDS, "A Sharper Point", "Enchant {count} swords.", "⚔️", 1, 3, 2.0D), "A Sharper Point", "Enchant {count} swords."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.AXES, "Arcane Axes", "Enchant {count} axes.", "🪓", 1, 3, 2.0D), "Arcane Axes", "Enchant {count} axes."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.PICKAXES, "Pick of the Magic", "Enchant {count} pickaxes.", "⛏️", 1, 3, 2.0D), "Pick of the Magic", "Enchant {count} pickaxes."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.SHOVELS, "Spellbound Shovels", "Enchant {count} shovels.", "🪏", 1, 3, 2.0D), "Spellbound Shovels", "Enchant {count} shovels."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.HOES, "Hocus-Crop-us", "Enchant {count} hoes.", "🌾", 1, 3, 2.0D), "Hocus-Crop-us", "Enchant {count} hoes."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.SPEARS, "Point Taken", "Enchant {count} spears.", "🔱", 1, 3, 2.0D), "Point Taken", "Enchant {count} spears."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.HEAD_ARMOR, "Head Full of Magic", "Enchant {count} helmets.", "⛑️", 1, 3, 2.0D), "Head Full of Magic", "Enchant {count} helmets."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.CHEST_ARMOR, "Protect the Core", "Enchant {count} chestplates.", "🦺", 1, 3, 2.0D), "Protect the Core", "Enchant {count} chestplates."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.LEG_ARMOR, "Leg Day, but Magical", "Enchant {count} leggings.", "👖", 1, 3, 2.0D), "Leg Day, but Magical", "Enchant {count} leggings."),
            option(4, false, false, 5, new EnchantItemTask(ItemTags.FOOT_ARMOR, "Best Foot Forward", "Enchant {count} pairs of boots.", "🥾", 1, 3, 2.0D), "Best Foot Forward", "Enchant {count} pairs of boots."),
            option(1, false, false, 6, new EnchantItemTask(ItemTags.SKULLS, "Cursed Cranium", "Enchant a wearable skull.", "💀", 1, 1, 3.0D), "Cursed Cranium", "Enchant a wearable skull."),
            option(4, false, false, 5, new EnchantItemTask(Items.BOW, "Drawn to Magic", "Enchant {count} bows.", "🏹", 1, 3, 2.0D), "Drawn to Magic", "Enchant {count} bows."),
            option(2, false, false, 5, new EnchantItemTask(Items.CROSSBOW, "Crossed Wires", "Enchant {count} crossbows.", "🎯", 1, 3, 2.0D), "Crossed Wires", "Enchant {count} crossbows."),
            option(2, false, false, 5, new EnchantItemTask(Items.TRIDENT, "Three Magic Points", "Enchant {count} tridents.", "🔱", 1, 2, 3.0D), "Three Magic Points", "Enchant {count} tridents."),
            option(2, false, false, 5, new EnchantItemTask(Items.FISHING_ROD, "Reel Enchantment", "Enchant {count} fishing rods.", "🎣", 1, 3, 2.0D), "Reel Enchantment", "Enchant {count} fishing rods."),
            option(2, false, false, 5, new EnchantItemTask(Items.MACE, "Heavy Magic", "Enchant {count} maces.", "🔨", 1, 2, 3.0D), "Heavy Magic", "Enchant {count} maces."),
            option(2, false, false, 5, new EnchantItemTask(Items.SHIELD, "A Spell to Hide Behind", "Enchant {count} shields.", "🛡️", 1, 3, 2.0D), "A Spell to Hide Behind", "Enchant {count} shields."),
            option(2, false, false, 5, new EnchantItemTask(Items.SHEARS, "Shear Sorcery", "Enchant {count} pairs of shears.", "✂️", 1, 3, 2.0D), "Shear Sorcery", "Enchant {count} pairs of shears."),
            option(2, false, false, 5, new EnchantItemTask(Items.BRUSH, "Brush with Magic", "Enchant {count} brushes.", "🖌️", 1, 3, 2.0D), "Brush with Magic", "Enchant {count} brushes."),
            option(2, false, false, 5, new EnchantItemTask(Items.FLINT_AND_STEEL, "Arcane Spark", "Enchant {count} flint and steels.", "🔥", 1, 3, 2.0D), "Arcane Spark", "Enchant {count} flint and steels."),
            option(4, false, true, 10, new EnchantItemTask(Items.ELYTRA, "Wings of Wonder", "Enchant an elytra.", "🪽", 1, 1, 4.0D), "Wings of Wonder", "Enchant an elytra."),
            option(1, false, false, 6, new EnchantItemTask(Items.CARROT_ON_A_STICK, "Carrot and Conjuring", "Enchant a carrot on a stick.", "🥕", 1, 1, 3.0D), "Carrot and Conjuring", "Enchant a carrot on a stick."),
            option(1, true, false, 8, new EnchantItemTask(Items.WARPED_FUNGUS_ON_A_STICK, "Warped Wand", "Enchant a warped fungus on a stick.", "🍄", 1, 1, 3.0D), "Warped Wand", "Enchant a warped fungus on a stick."),
            option(1, false, false, 6, new EnchantItemTask(Items.COMPASS, "Occult Bearings", "Enchant a compass.", "🧭", 1, 1, 3.0D), "Occult Bearings", "Enchant a compass."),
            option(3, false, false, 6, new EnchantItemTask(Items.CARVED_PUMPKIN, "Hexed Headwear", "Enchant a carved pumpkin.", "🎃", 1, 1, 3.0D), "Hexed Headwear", "Enchant a carved pumpkin."),
            option(2, false, false, 5, new EnchantItemTask(Items.ENCHANTED_BOOK, "Write a Spellbook", "Enchant {count} books.", "📖", 1, 3, 2.0D), "Write a Spellbook", "Enchant {count} books."),

            // Uncommon hostile mobs and bosses
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ALLAY, "an Allay", "Allays", "Defeat an allay. It was probably plotting something melodious.", "🧚", 1, 1, 8.0D), "Defeat an Allay", "Defeat an allay. It was probably plotting something melodious."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ARMADILLO, "an Armadillo", "Armadillos", "Defeat an armadillo. First, convince it to stop being a cube.", "🦔", 1, 1, 6.0D), "Defeat an Armadillo", "Defeat an armadillo. First, convince it to stop being a cube."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.AXOLOTL, "an Axolotl", "Axolotls", "Defeat an axolotl. You monster.", "🦎", 1, 1, 6.0D), "Defeat an Axolotl", "Defeat an axolotl. You monster."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.CAT, "a Cat", "Cats", "Defeat a cat. It has eight more lives anyway.", "🐈", 1, 1, 5.0D), "Defeat a Cat", "Defeat a cat. It has eight more lives anyway."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.DOLPHIN, "a Dolphin", "Dolphins", "Defeat a dolphin. Expect poor reviews from the ocean.", "🐬", 1, 1, 6.0D), "Defeat a Dolphin", "Defeat a dolphin. Expect poor reviews from the ocean."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.DONKEY, "a Donkey", "Donkeys", "Defeat a donkey. Check the saddlebags first.", "🫏", 1, 1, 5.0D), "Defeat a Donkey", "Defeat a donkey. Check the saddlebags first."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.GLOW_SQUID, "a Glow Squid", "Glow Squid", "Defeat {count} glow squid. Their campaign promise has expired.", "🦑", 2, 5, 2.0D), "Defeat Glow Squid", "Defeat {count} glow squid. Their campaign promise has expired."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.MOOSHROOM, "a Mooshroom", "Mooshrooms", "Defeat a mooshroom. Finding the island was the hard part.", "🍄", 1, 1, 14.0D), "Defeat a Mooshroom", "Defeat a mooshroom. Finding the island was the hard part."),
            option(2, false, false, 5, new KillEntityTask(EntityTypes.SNIFFER, "a Sniffer", "Sniffers", "Defeat a sniffer. This task has a personal vendetta.", "🐽", 1, 1, 10.0D), "Defeat a Sniffer", "Defeat a sniffer. This task has a personal vendetta."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.TRADER_LLAMA, "a Trader Llama", "Trader Llamas", "Defeat a trader llama. Duck before it files a complaint.", "🦙", 1, 1, 6.0D), "Defeat a Trader Llama", "Defeat a trader llama. Duck before it files a complaint."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.NAUTILUS, "a Nautilus", "Nautiluses", "Defeat a nautilus. The spiral did nothing wrong.", "🐚", 1, 1, 9.0D), "Defeat a Nautilus", "Defeat a nautilus. The spiral did nothing wrong."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ZOMBIE_NAUTILUS, "a Zombie Nautilus", "Zombie Nautiluses", "Defeat a zombie nautilus and end its second voyage.", "🧟", 1, 1, 12.0D), "Defeat a Zombie Nautilus", "Defeat a zombie nautilus and end its second voyage."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.HAPPY_GHAST, "a Happy Ghast", "Happy Ghasts", "Defeat a happy ghast. Happiness was temporary.", "😊", 1, 1, 14.0D), "Defeat a Happy Ghast", "Defeat a happy ghast. Happiness was temporary."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.BLAZE, "a Blaze", "Blazes", "🔥", 4, 8, 2.5D), "Defeat Blazes", "Defeat {count} blazes."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.BOGGED, "a Bogged", "Bogged", "🏹", 2, 4, 4.0D), "Defeat Bogged", "Defeat {count} bogged."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.BREEZE, "a Breeze", "Breezes", "🌬️", 2, 4, 4.0D), "Defeat Breezes", "Defeat {count} breezes."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.CREEPER, "a Creeper", "Creepers", "💥", 3, 7, 2.5D), "Defeat Creepers", "Defeat {count} creepers."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.GUARDIAN, "a Guardian", "Guardians", "🔱", 3, 7, 3.0D), "Defeat Guardians", "Defeat {count} guardians."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.MAGMA_CUBE, "a Magma Cube", "Magma Cubes", "🟧", 4, 10, 2.0D), "Defeat Magma Cubes", "Defeat {count} magma cubes."),
            option(2, false, false, 5, new KillEntityTask(EntityTypes.PARCHED, "a Parched", "Parched", "🏜️", 1, 2, 6.0D), "Defeat Parched", "Defeat {count} parched."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.PIGLIN, "a Piglin", "Piglins", "🐽", 3, 6, 2.5D), "Defeat Piglins", "Defeat {count} piglins."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.PIGLIN_BRUTE, "a Piglin Brute", "Piglin Brutes", "🪓", 1, 2, 5.0D), "Defeat Piglin Brutes", "Defeat {count} piglin brutes."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.PILLAGER, "a Pillager", "Pillagers", "🏴", 3, 8, 2.5D), "Defeat Pillagers", "Defeat {count} pillagers."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.SKELETON, "a Skeleton", "Skeletons", "💀", 4, 9, 2.0D), "Defeat Skeletons", "Defeat {count} skeletons."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.SLIME, "a Slime", "Slimes", "🟩", 6, 15, 1.25D), "Defeat Slimes", "Defeat {count} slimes."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.SPIDER, "a Spider", "Spiders", "🕷️", 3, 8, 2.5D), "Defeat Spiders", "Defeat {count} spiders."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.SULFUR_CUBE, "a Sulfur Cube", "Sulfur Cubes", "Defeat {count} sulfur cubes and watch them split.", "🟨", 2, 5, 2.0D), "Defeat Sulfur Cubes", "Defeat {count} sulfur cubes and watch them split."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.VEX, "a Vex", "Vexes", "🪽", 1, 2, 6.0D), "Defeat Vexes", "Defeat {count} vexes."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.VINDICATOR, "a Vindicator", "Vindicators", "🪓", 1, 2, 6.0D), "Defeat Vindicators", "Defeat {count} vindicators."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.WARDEN, "the Warden", "Wardens", "Defeat the Warden. Sneaking away remains the sensible option.", "📡", 1, 1, 25.0D), "Defeat the Warden", "Defeat the Warden. Sneaking away remains the sensible option."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.WITHER_SKELETON, "a Wither Skeleton", "Wither Skeletons", "☠️", 3, 6, 3.5D), "Defeat Wither Skeletons", "Defeat {count} wither skeletons."),
            option(2, true, false, 8, new KillEntityTask(EntityTypes.ZOGLIN, "a Zoglin", "Zoglins", "🐗", 1, 2, 6.0D), "Defeat Zoglins", "Defeat {count} zoglins."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ZOMBIE, "a Zombie", "Zombies", "🧟", 4, 10, 2.0D), "Defeat Zombies", "Defeat {count} zombies."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ZOMBIE_VILLAGER, "a Zombie Villager", "Zombie Villagers", "🧟", 1, 2, 5.0D), "Defeat Zombie Villagers", "Defeat {count} zombie villagers."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.ZOMBIFIED_PIGLIN, "a Zombified Piglin", "Zombified Piglins", "🧟", 3, 7, 2.5D), "Defeat Zombified Piglins", "Defeat {count} zombified piglins."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.HUSK, "a Husk", "Husks", "🏜️", 2, 5, 5.0D), "Defeat Husks", "Defeat {count} husks."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.STRAY, "a Stray", "Strays", "🏹", 2, 4, 5.0D), "Defeat Strays", "Defeat {count} strays."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.DROWNED, "a Drowned", "Drowned", "🔱", 2, 5, 4.0D), "Defeat Drowned", "Defeat {count} drowned."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.WITCH, "a Witch", "Witches", "🧙", 1, 3, 7.0D), "Defeat Witches", "Defeat {count} witches."),
            option(3, false, false, 5, new KillEntityTask(EntityTypes.PHANTOM, "a Phantom", "Phantoms", "🌙", 1, 4, 5.0D), "Defeat Phantoms", "Defeat {count} phantoms."),
            option(2, false, false, 5, new KillEntityTask(EntityTypes.SILVERFISH, "a Silverfish", "Silverfish", "🪲", 3, 8, 2.0D), "Defeat Silverfish", "Defeat {count} silverfish."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.CAVE_SPIDER, "a Cave Spider", "Cave Spiders", "🕷️", 2, 5, 4.0D), "Defeat Cave Spiders", "Defeat {count} cave spiders."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.ELDER_GUARDIAN, "an Elder Guardian", "Elder Guardians", "🐟", 1, 1, 20.0D), "Defeat an Elder Guardian", "Defeat an elder guardian."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.RAVAGER, "a Ravager", "Ravagers", "🐂", 1, 2, 10.0D), "Defeat Ravagers", "Defeat {count} ravagers."),
            option(1, false, false, 5, new KillEntityTask(EntityTypes.EVOKER, "an Evoker", "Evokers", "🪄", 1, 1, 15.0D), "Defeat an Evoker", "Defeat an evoker."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.GHAST, "a Ghast", "Ghasts", "👻", 1, 3, 7.0D), "Defeat Ghasts", "Defeat {count} ghasts."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.HOGLIN, "a Hoglin", "Hoglins", "🐗", 2, 5, 4.0D), "Defeat Hoglins", "Defeat {count} hoglins."),
            option(1, true, false, 8, new KillEntityTask(EntityTypes.WITHER, "the Wither", "Withers", "💀", 1, 1, 28.0D), "Defeat the Wither", "Defeat the wither."),
            option(2, false, true, 10, new KillEntityTask(EntityTypes.ENDERMITE, "an Endermite", "Endermites", "🟣", 2, 5, 5.0D), "Defeat Endermites", "Defeat {count} endermites."),
            option(1, false, true, 10, new KillEntityTask(EntityTypes.SHULKER, "a Shulker", "Shulkers", "📦", 2, 5, 6.0D), "Defeat Shulkers", "Defeat {count} shulkers."),
            option(1, false, true, 10, new KillEntityTask(EntityTypes.ENDER_DRAGON, "the Ender Dragon", "Ender Dragons", "🐉", 1, 1, 30.0D), "Defeat the Ender Dragon", "Defeat the ender dragon."),

            // Effects
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.SPEED, "Speed", "💨", 3.0D), "Experience Speed", "Get the Speed effect from any source."),
            option(5, false, false, 5, new ReceiveEffectTask(MobEffects.DARKNESS, "Darkness", "🌑", 8.0D), "Experience Darkness", "Get the Darkness effect from any source."),
            option(5, false, true, 10, new ReceiveEffectTask(MobEffects.LEVITATION, "Levitation", "🎈", 8.0D), "Experience Levitation", "Get the Levitation effect from any source."),
            option(5, false, false, 5, new ReceiveEffectTask(MobEffects.GLOWING, "Glowing", "✨", 6.0D), "Experience Glowing", "Get the Glowing effect from any source."),
            option(3, false, false, 5, new ReceiveEffectTask(MobEffects.NAUSEA, "Nausea", "🌀", 5.0D), "Experience Nausea", "Get the Nausea effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.DOLPHINS_GRACE, "Dolphin's Grace", "🐬", 5.0D), "Experience Dolphin's Grace", "Get the Dolphin's Grace effect from any source."),
            option(1, false, false, 5, new ReceiveEffectTask(MobEffects.BREATH_OF_THE_NAUTILUS, "Breath of the Nautilus", "🐚", 6.0D), "Experience Breath of the Nautilus", "Get the Breath of the Nautilus effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.BAD_OMEN, "Bad Omen", "🏴", 5.0D), "Experience Bad Omen", "Get the Bad Omen effect from any source."),
            option(4, false, false, 5, new ReceiveEffectTask(MobEffects.RAID_OMEN, "Raid Omen", "🏰", 6.0D), "Experience Raid Omen", "Get the Raid Omen effect from any source."),
            option(4, false, false, 5, new ReceiveEffectTask(MobEffects.TRIAL_OMEN, "Trial Omen", "🔑", 6.0D), "Experience Trial Omen", "Get the Trial Omen effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.HASTE, "Haste", "⛏️", 6.0D), "Experience Haste", "Get the Haste effect from any source."),
            option(4, true, false, 8, new ReceiveEffectTask(MobEffects.INVISIBILITY, "Invisibility", "👻", 5.0D), "Experience Invisibility", "Get the Invisibility effect from any source."),
            option(2, true, false, 8, new ReceiveEffectTask(MobEffects.WATER_BREATHING, "Water Breathing", "🌊", 5.0D), "Experience Water Breathing", "Get the Water Breathing effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.WIND_CHARGED, "Wind Charged", "🌬️", 5.0D), "Experience Wind Charged", "Get the Wind Charged effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.WITHER, "Wither", "☠️", 6.0D), "Experience Wither", "Get the Wither effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.INFESTED, "Infested", "🪲", 5.0D), "Experience Infested", "Get the Infested effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.OOZING, "Oozing", "🟢", 5.0D), "Experience Oozing", "Get the Oozing effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.WEAVING, "Weaving", "🕸️", 5.0D), "Experience Weaving", "Get the Weaving effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.CONDUIT_POWER, "Conduit Power", "🔱", 6.0D), "Experience Conduit Power", "Get the Conduit Power effect from any source."),
            option(2, false, false, 5, new ReceiveEffectTask(MobEffects.NIGHT_VISION, "Night Vision", "👁️", 2.0D), "Experience Night Vision", "Get the Night Vision effect from any source."),

            // Experience and breeding
            option(3, false, false, 5, new GainLevelsTask(), "Learn Something New", "Gain {count} experience levels."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.RABBIT, "Rabbits", "🐇", 1, 3, 3.0D), "Breed Rabbits", "Breed {count} rabbits."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.COW, "Cows", "🐄", 2, 4, 3.0D), "Breed Cows", "Breed {count} cows."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.SHEEP, "Sheep", "🐑", 2, 4, 3.0D), "Breed Sheep", "Breed {count} sheep."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.PIG, "Pigs", "🐖", 2, 4, 3.0D), "Breed Pigs", "Breed {count} pigs."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.CHICKEN, "Chickens", "🐔", 2, 5, 2.5D), "Breed Chickens", "Breed {count} chickens."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.GOAT, "Goats", "🐐", 1, 2, 5.0D), "Breed Goats", "Breed {count} goats."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.DONKEY, "Donkeys", "🫏", 1, 1, 8.0D), "Breed Donkeys", "Breed {count} donkeys."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.WOLF, "Wolves", "🐺", 1, 2, 5.0D), "Breed Wolves", "Breed {count} wolves."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.CAT, "Cats", "🐈", 1, 2, 5.0D), "Breed Cats", "Breed {count} cats."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.AXOLOTL, "Axolotls", "🦎", 1, 1, 9.0D), "Breed Axolotls", "Breed {count} axolotls."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.TURTLE, "Turtles", "🐢", 1, 1, 9.0D), "Breed Turtles", "Breed {count} turtles."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.PANDA, "Pandas", "🐼", 1, 1, 9.0D), "Breed Pandas", "Breed {count} pandas."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.BEE, "Bees", "🐝", 2, 4, 3.5D), "Breed Bees", "Breed {count} bees."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.FOX, "Foxes", "🦊", 1, 2, 6.0D), "Breed Foxes", "Breed {count} foxes."),
            option(1, true, false, 8, new BreedEntityTask(EntityTypes.STRIDER, "Striders", "🟥", 1, 2, 7.0D), "Breed Striders", "Breed {count} striders."),
            option(1, true, false, 8, new BreedEntityTask(EntityTypes.HOGLIN, "Hoglins", "🐗", 1, 2, 7.0D), "Breed Hoglins", "Breed {count} hoglins."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.SNIFFER, "Sniffers", "🐽", 1, 1, 10.0D), "Breed Sniffers", "Breed {count} sniffers."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.FROG, "Frogs", "🐸", 1, 2, 6.0D), "Breed Frogs", "Breed {count} frogs."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.CAMEL, "Camels", "🐫", 1, 1, 8.0D), "Breed Camels", "Breed {count} camels."),
            option(2, false, false, 5, new BreedEntityTask(EntityTypes.ARMADILLO, "Armadillos", "🦔", 1, 2, 6.0D), "Breed Armadillos", "Breed {count} armadillos."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.GOAT, "Goats", "🐐", 2, 3, 2.0D), "Feed Goats", "Offer food to {count} goats; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.DONKEY, "Donkeys", "🫏", 2, 3, 2.25D), "Feed Donkeys", "Offer food to {count} donkeys; each right-click with valid food counts, even if the animal is full."),
            option(3, false, false, 5, new FeedEntityTask(EntityTypes.WOLF, "Wolves", "🐺", 2, 3, 2.0D), "Feed Wolves", "Offer food to {count} wolves; each right-click with valid food counts, even if the animal is full."),
            option(3, false, false, 5, new FeedEntityTask(EntityTypes.CAT, "Cats", "🐈", 2, 3, 2.0D), "Feed Cats", "Offer food to {count} cats; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.AXOLOTL, "Axolotls", "🦎", 1, 2, 3.25D), "Feed Axolotls", "Offer food to {count} axolotls; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.RABBIT, "Rabbits", "🐇", 2, 3, 2.25D), "Feed Rabbits", "Offer food to {count} rabbits; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.TURTLE, "Turtles", "🐢", 1, 2, 3.25D), "Feed Turtles", "Offer food to {count} turtles; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.PANDA, "Pandas", "🐼", 1, 2, 3.25D), "Feed Pandas", "Offer food to {count} pandas; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.BEE, "Bees", "🐝", 2, 3, 2.25D), "Feed Bees", "Offer food to {count} bees; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.FOX, "Foxes", "🦊", 1, 2, 3.25D), "Feed Foxes", "Offer food to {count} foxes; each right-click with valid food counts, even if the animal is full."),
            option(1, true, false, 8, new FeedEntityTask(EntityTypes.STRIDER, "Striders", "🟥", 1, 2, 4.0D), "Feed Striders", "Offer food to {count} striders; each right-click with valid food counts, even if the animal is full."),
            option(1, true, false, 8, new FeedEntityTask(EntityTypes.HOGLIN, "Hoglins", "🐗", 1, 2, 4.0D), "Feed Hoglins", "Offer food to {count} hoglins; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.SNIFFER, "Sniffers", "🐽", 1, 2, 4.0D), "Feed Sniffers", "Offer food to {count} sniffers; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.FROG, "Frogs", "🐸", 1, 2, 3.25D), "Feed Frogs", "Offer food to {count} frogs; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 8, new FeedEntityTask(EntityTypes.CAMEL, "Camels", "🐫", 1, 4, 2.0D), "Feed Camels", "Offer food to {count} camels; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.ARMADILLO, "Armadillos", "🦔", 1, 2, 3.25D), "Feed Armadillos", "Offer food to {count} armadillos; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.COW, "Cows", "🐄", 2, 3, 2.25D), "Feed Cows", "Offer food to {count} cows; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.SHEEP, "Sheep", "🐑", 2, 3, 2.25D), "Feed Sheep", "Offer food to {count} sheep; each right-click with valid food counts, even if the animal is full."),
            option(2, false, false, 5, new FeedEntityTask(EntityTypes.NAUTILUS, "Nautiluses", "🐚", 1, 2, 4.0D), "Feed Nautiluses", "Offer food to {count} nautiluses; each right-click with valid food counts, even if the animal is full."),

            // Villager trades
            // Do not use trades that require a random color. Use reliable high-level trades and give a high reward.
            option(1, false, false, 5, new VillagerTradeTask(Items.STONE_HOE, "Stone Hoes", 1, 2, 3.0D), "Trade for Stone Hoes", "Receive {count} stone hoes from villager trades."),
            option(1, false, false, 5, new VillagerTradeTask(Items.SUSPICIOUS_STEW, "Suspicious Stew", 1, 2, 3.0D), "Trade for Suspicious Stew", "Receive {count} suspicious stew from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.MASON, "Mason", 1, 3, 2.0D), "Visit a Mason", "Complete {count} trades with a mason."),
            option(10, false, false, 5, new VillagerTradeTask(VillagerTradeTask.Mode.RECEIVE_EMERALDS, 8, 24, 0.5D), "Emerald Exporter", "Receive {count} emeralds from villager trades."),
            option(10, false, false, 5, new VillagerTradeTask(VillagerTradeTask.Mode.SPEND_EMERALDS, 8, 24, 0.5D), "Support Local Villagers", "Spend {count} emeralds in villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.GLISTERING_MELON_SLICE, "Glistering Melon Slices", 1, 4, 4.0D), "Trade for Glistering Melon Slices", "Receive {count} glistering melon slices from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.RABBIT_STEW, "Rabbit Stew", 1, 3, 2.5D), "Trade for Rabbit Stew", "Receive {count} rabbit stew from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.DRIED_KELP_BLOCK, "Dried Kelp Blocks", 1, 4, 1.5D), "Trade for Dried Kelp Blocks", "Receive {count} dried kelp blocks from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.FISHING_ROD, "Fishing Rods", 1, 1, 6.0D), "Trade for Fishing Rods", "Receive {count} fishing rods from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.NAME_TAG, "Name Tags", 1, 3, 6.0D), "Trade for Name Tags", "Receive {count} name tags from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.LANTERN, "Lanterns", 2, 6, 1.25D), "Trade for Lanterns", "Receive {count} lanterns from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.BELL, "Bells", 1, 1, 7.0D), "Trade for Bells", "Receive {count} bells from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.CROSSBOW, "Crossbows", 1, 2, 3.0D), "Trade for Crossbows", "Receive {count} crossbows from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.TIPPED_ARROW, "Tipped Arrows", 5, 15, 1.0D), "Trade for Tipped Arrows", "Receive {count} tipped arrows from villager trades."),
            option(3, false, false, 5, new VillagerTradeTask(Items.FILLED_MAP, "Explorer Maps", 1, 1, 9.0D), "Trade for Explorer Maps", "Receive {count} explorer maps from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.EXPERIENCE_BOTTLE, "Bottles o' Enchanting", 2, 8, 2.0D), "Trade for Bottles o' Enchanting", "Receive {count} bottles o' enchanting from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.GLOWSTONE, "Glowstone", 2, 8, 1.0D), "Trade for Glowstone", "Receive {count} glowstone from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.LEATHER_HORSE_ARMOR, "Leather Horse Armor", 1, 1, 10.0D), "Trade for Leather Horse Armor", "Receive {count} leather horse armor from villager trades."),
            option(2, false, false, 5, new VillagerTradeTask(Items.PAINTING, "Paintings", 1, 4, 4.0D), "Trade for Paintings", "Receive {count} paintings from villager trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.INK_SAC, "Ink Sacs", 3, 12, 0.6D), "Trade Away Ink Sacs", "Give villagers {count} ink sacs in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.DIAMOND, "Diamonds", 1, 2, 4.0D), "Trade Away Diamonds", "Give villagers {count} diamonds in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.FLINT, "Flint", 4, 16, 0.5D), "Trade Away Flint", "Give villagers {count} flint in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.FEATHER, "Feathers", 6, 24, 0.35D), "Trade Away Feathers", "Give villagers {count} feathers in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.TRIPWIRE_HOOK, "Tripwire Hooks", 2, 8, 0.8D), "Trade Away Tripwire Hooks", "Give villagers {count} tripwire hooks in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.ROTTEN_FLESH, "Rotten Flesh", 8, 32, 0.25D), "Trade Away Rotten Flesh", "Give villagers {count} rotten flesh in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.RABBIT_FOOT, "Rabbit's Feet", 1, 4, 2.0D), "Trade Away Rabbit's Feet", "Give villagers {count} rabbit's feet in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.RABBIT_HIDE, "Rabbit Hide", 3, 12, 0.75D), "Trade Away Rabbit Hide", "Give villagers {count} rabbit hide in trades."),
            option(2, true, false, 8, VillagerTradeTask.give(Items.QUARTZ, "Nether Quartz", 4, 16, 0.6D), "Trade Away Nether Quartz", "Give villagers {count} nether quartz in trades."),
            option(2, false, false, 5, VillagerTradeTask.give(Items.GRANITE, "Granite", 8, 24, 0.3D), "Trade Away Granite", "Give villagers {count} granite in trades."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.ARMORER, "Armorer", 1, 4, 2.0D), "Visit a Armorer", "Complete {count} trades with a armorer."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.BUTCHER, "Butcher", 1, 4, 2.0D), "Visit a Butcher", "Complete {count} trades with a butcher."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.CARTOGRAPHER, "Cartographer", 1, 4, 2.0D), "Visit a Cartographer", "Complete {count} trades with a cartographer."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.CLERIC, "Cleric", 1, 4, 2.0D), "Visit a Cleric", "Complete {count} trades with a cleric."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.FARMER, "Farmer", 1, 4, 2.0D), "Visit a Farmer", "Complete {count} trades with a farmer."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.FISHERMAN, "Fisherman", 1, 4, 2.0D), "Visit a Fisherman", "Complete {count} trades with a fisherman."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.FLETCHER, "Fletcher", 1, 4, 2.0D), "Visit a Fletcher", "Complete {count} trades with a fletcher."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.LEATHERWORKER, "Leatherworker", 1, 4, 2.0D), "Visit a Leatherworker", "Complete {count} trades with a leatherworker."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.LIBRARIAN, "Librarian", 1, 4, 2.0D), "Visit a Librarian", "Complete {count} trades with a librarian."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.SHEPHERD, "Shepherd", 1, 4, 2.0D), "Visit a Shepherd", "Complete {count} trades with a shepherd."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.TOOLSMITH, "Toolsmith", 1, 4, 2.0D), "Visit a Toolsmith", "Complete {count} trades with a toolsmith."),
            option(2, false, false, 5, new VillagerTradeTask(VillagerProfession.WEAPONSMITH, "Weaponsmith", 1, 4, 2.0D), "Visit a Weaponsmith", "Complete {count} trades with a weaponsmith."),

            // Brewing
            option(2, true, false, 8, new BrewPotionTask(Potions.NIGHT_VISION, "Night Vision", 1, 3, 3.0D), "Brew Night Vision", "Brew and collect {count} potions of Night Vision."),
            option(2, true, false, 8, new BrewPotionTask(Potions.INVISIBILITY, "Invisibility", 1, 3, 4.0D), "Brew Invisibility", "Brew and collect {count} potions of Invisibility."),
            option(2, true, false, 8, new BrewPotionTask(Potions.LEAPING, "Leaping", 1, 3, 3.5D), "Brew Leaping", "Brew and collect {count} potions of Leaping."),
            option(2, true, false, 8, new BrewPotionTask(Potions.FIRE_RESISTANCE, "Fire Resistance", 1, 3, 3.5D), "Brew Fire Resistance", "Brew and collect {count} potions of Fire Resistance."),
            option(2, true, false, 8, new BrewPotionTask(Potions.SWIFTNESS, "Swiftness", 1, 3, 3.0D), "Brew Swiftness", "Brew and collect {count} potions of Swiftness."),
            option(2, true, false, 8, new BrewPotionTask(Potions.SLOWNESS, "Slowness", 1, 3, 4.0D), "Brew Slowness", "Brew and collect {count} potions of Slowness."),
            option(2, true, false, 8, new BrewPotionTask(Potions.HEALING, "Healing", 1, 3, 3.5D), "Brew Healing", "Brew and collect {count} potions of Healing."),
            option(2, true, false, 8, new BrewPotionTask(Potions.HARMING, "Harming", 1, 3, 4.0D), "Brew Harming", "Brew and collect {count} potions of Harming."),
            option(2, true, false, 8, new BrewPotionTask(Potions.POISON, "Poison", 1, 3, 3.5D), "Brew Poison", "Brew and collect {count} potions of Poison."),
            option(2, true, false, 8, new BrewPotionTask(Potions.REGENERATION, "Regeneration", 1, 3, 4.0D), "Brew Regeneration", "Brew and collect {count} potions of Regeneration."),
            option(2, true, false, 8, new BrewPotionTask(Potions.STRENGTH, "Strength", 1, 3, 3.5D), "Brew Strength", "Brew and collect {count} potions of Strength."),
            option(2, true, false, 8, new BrewPotionTask(Potions.WIND_CHARGED, "Wind Charging", 1, 2, 5.0D), "Brew Wind Charging", "Brew and collect {count} potions of Wind Charging."),
            option(2, true, false, 8, new BrewPotionTask(Potions.WEAVING, "Weaving", 1, 2, 5.0D), "Brew Weaving", "Brew and collect {count} potions of Weaving."),
            option(2, true, false, 8, new BrewPotionTask(Potions.OOZING, "Oozing", 1, 2, 5.0D), "Brew Oozing", "Brew and collect {count} potions of Oozing."),
            option(2, true, false, 8, new BrewPotionTask(Potions.INFESTED, "Infestation", 1, 2, 5.0D), "Brew Infestation", "Brew and collect {count} potions of Infestation."),
            option(2, true, false, 8, new BrewPotionTask(Potions.SLOW_FALLING, "Slow Falling", 1, 3, 4.0D), "Brew Slow Falling", "Brew and collect {count} potions of Slow Falling."),
            option(2, true, false, 8, new BrewPotionTask(Potions.TURTLE_MASTER, "the Turtle Master", 1, 2, 5.0D), "Brew the Turtle Master", "Brew and collect {count} potions of the Turtle Master."),
            option(2, true, false, 8, new BrewPotionTask(Potions.WEAKNESS, "Weakness", 1, 3, 3.5D), "Brew Weakness", "Brew and collect {count} potions of Weakness."),
            option(2, true, false, 8, new BrewPotionTask(Potions.WATER_BREATHING, "Water Breathing", 1, 3, 3.5D), "Brew Water Breathing", "Brew and collect {count} potions of Water Breathing."),

            // Archaeology and fishing
            option(4, false, false, 8, new BrushBlockTask(Blocks.SUSPICIOUS_SAND, "Suspicious Sand"), "Brush Suspicious Sand", "Brush suspicious sand {count} times."),
            option(4, false, false, 8, new BrushBlockTask(Blocks.SUSPICIOUS_GRAVEL, "Suspicious Gravel"), "Brush Suspicious Gravel", "Brush suspicious gravel {count} times."),
            option(10, false, false, 5, new FishTask(5, 12, 1D), "Gone Fishing", "Catch something while fishing {count} times."),
            option(1, false, false, 10, new FishTask(Items.COD, "Cod", 1, 1, 5.0D), "Catch Cod", "Catch {count} cod while fishing."),
            option(1, false, false, 10, new FishTask(Items.SALMON, "Salmon", 1, 1, 5.0D), "Catch Salmon", "Catch {count} salmon while fishing."),
            option(1, false, false, 10, new FishTask(Items.PUFFERFISH, "Pufferfish", 1, 1, 7.0D), "Catch Pufferfish", "Catch {count} pufferfish while fishing."),
            option(1, false, false, 10, new FishTask(Items.TROPICAL_FISH, "Tropical Fish", 1, 1, 7.0D), "Catch Tropical Fish", "Catch {count} tropical fish while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-albacore", "Albacore", 1, 1, 5.0D), "Catch Albacore", "Catch {count} albacore while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-bass", "Bass", 1, 1, 4.0D), "Catch Bass", "Catch {count} bass while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-carp", "Carp", 1, 1, 4.0D), "Catch Carp", "Catch {count} carp while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-anchovy", "Anchovy", 1, 1, 4.0D), "Catch Anchovy", "Catch {count} anchovy while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-herring", "Herring", 1, 1, 4.0D), "Catch Herring", "Catch {count} herring while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-perch", "Perch", 1, 1, 4.0D), "Catch Perch", "Catch {count} perch while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-pike", "Pike", 1, 1, 4.0D), "Catch Pike", "Catch {count} pike while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-tuna", "Tuna", 1, 1, 4.0D), "Catch Tuna", "Catch {count} tuna while fishing."),
            option(1, false, false, 10, FishTask.custom("fish-red_snapper", "Red Snapper", 1, 1, 4.0D), "Catch Red Snapper", "Catch {count} red snapper while fishing."),

            // Mining
            option(3, false, false, 5, new BreakBlockTask(Blocks.SPAWNER, "a Spawner", "🔥", 1, 1, 12.0D), "Mine a Spawner", "Mine {count} a spawner."),
            option(3, false, false, 5, new BreakBlockTask(Blocks.INFESTED_STONE, "Infested Stone", "🪲", 1, 5, 3.0D), "Mine Infested Stone", "Mine {count} infested stone."),
            option(3, false, false, 5, new BreakBlockTask(Blocks.BUDDING_AMETHYST, "Budding Amethyst", "💎", 1, 1, 10.0D), "Mine Budding Amethyst", "Mine {count} budding amethyst."),
            option(1, false, false, 5, new BreakBlockTask(Blocks.REINFORCED_DEEPSLATE, "Reinforced Deepslate", "⬛", 1, 1, 12.0D), "Mine Reinforced Deepslate", "Mine {count} reinforced deepslate."),
            option(3, true, false, 8, new BreakBlockTask(Blocks.GILDED_BLACKSTONE, "Gilded Blackstone", "🟨", 1, 2, 6.0D), "Mine Gilded Blackstone", "Mine {count} gilded blackstone."),
            option(3, true, false, 8, new BreakBlockTask(Blocks.CRYING_OBSIDIAN, "Crying Obsidian", "🟪", 1, 3, 4.0D), "Mine Crying Obsidian", "Mine {count} crying obsidian."),
            option(4, true, false, 8, new BreakBlockTask(Blocks.ANCIENT_DEBRIS, "Ancient Debris", "🟫", 1, 2, 8.0D), "Mine Ancient Debris", "Mine {count} ancient debris."),

            // Direct entity and world actions
            option(4, false, false, 5, simple(DailySimpleEvent.SHEAR_SHEEP, "Shear Sheep", "Shear {count} sheep. Seasonal haircuts are important.", "🐑", 4, 10, 0.75D, "Sheared", "sheep"), "Shear Sheep", "Shear {count} sheep. Seasonal haircuts are important."),
            option(4, false, false, 5, simple(DailySimpleEvent.IGNITE_CREEPER, "Ignite Creepers", "Ignite {count} creepers and give them a moment to reconsider.", "🧨", 2, 5, 2.0D, "Ignited", "creepers"), "Ignite Creepers", "Ignite {count} creepers and give them a moment to reconsider."),
            option(3, true, false, 8, simple(DailySimpleEvent.REFLECT_GHAST_FIREBALL, "Return to Sender", "Reflect a ghast fireball. Postage is already paid.", "🔥", 1, 1, 7.0D, "Reflected", "fireball"), "Return to Sender", "Reflect a ghast fireball. Postage is already paid."),
            option(3, false, false, 5, new UseItemTask(Items.ENDER_PEARL, "Ender Pearls", "🟢", 2, 6, 1.25D), "Use Ender Pearls", "Use ender pearls {count} times."),
            option(4, false, false, 5, simple(DailySimpleEvent.JUMP_SLIME_BLOCK, "Bouncy Business", "Bounce on a slime block {count} times. Build up a rhythm.", "🟩", 6, 18, 0.5D, "Bounced", "times"), "Bouncy Business", "Bounce on a slime block {count} times. Build up a rhythm."),
            option(3, false, false, 5, simple(DailySimpleEvent.DEFEAT_RAID, "Defeat a Raid", "Help a village survive a raid.", "🏰", 1, 1, 12.0D, "Defeated", "raid"), "Defeat a Raid", "Help a village survive a raid."),
            option(3, false, false, 5, new UseItemTask(Items.WIND_CHARGE, "Wind Charges", "💨", 3, 10, 0.8D), "Use Wind Charges", "Use wind charges {count} times."),
            option(3, false, false, 5, new UseItemTask(Items.SPYGLASS, "a Spyglass", "🔭", 3, 10, 0.6D), "Use a Spyglass", "Use a spyglass {count} times."),
            option(3, false, false, 5, simple(DailySimpleEvent.LIGHT_TNT, "Light TNT", "Light {count} TNT with flint and steel. Stand at a professionally responsible distance.", "💥", 2, 5, 1.5D, "Lit", "TNT"), "Light TNT", "Light {count} TNT with flint and steel. Stand at a professionally responsible distance."),
            option(3, false, false, 5, simple(DailySimpleEvent.RENAME_TOOL, "Name Your Tool", "Give one of your tools a nice name. It has earned one.", "🏷️", 1, 1, 5.0D, "Renamed", "tool"), "Name Your Tool", "Give one of your tools a nice name. It has earned one."),
            option(3, false, false, 5, simple(DailySimpleEvent.LIGHT_CANDLE, "Light Candles", "Light {count} candles with flint and steel and make the room feel finished.", "🕯️", 2, 6, 1.0D, "Lit", "candles"), "Light Candles", "Light {count} candles with flint and steel and make the room feel finished."),

            // Custom potions
            option(1, false, false, 5, new UseCharmTask(DailyCharm.DISPLACEMENT, "🧪", 12.0D), "Use a Potion of Displacement", "Successfully use a Potion of Displacement."),
            option(1, false, false, 5, new UseCharmTask(DailyCharm.RETURNING, "🏠", 10.0D), "Use a Potion of Returning", "Successfully use a Potion of Returning."),
            option(1, false, false, 5, new UseCharmTask(DailyCharm.RESONANCE, "📡", 12.0D), "Use a Potion of Resonance", "Successfully use a Potion of Resonance."),
            option(1, false, false, 5, new UseCharmTask(DailyCharm.INSOMNIA, "🦇", 14.0D), "Use a Potion of Insomnia", "Successfully use a Potion of Insomnia."),

            // Riding and animal care
            option(6, false, false, 5, new RideDistanceTask(EntityTypes.MINECART, "Minecart", "🛤️", 150, 500, 0.02D), "Ride a Minecart", "Travel {count} blocks while riding a minecart."),
            option(4, false, false, 5, new RideDistanceTask(EntityTypes.PIG, "Pig", "🐖", 60, 200, 0.035D), "Ride a Pig", "Travel {count} blocks while riding a pig."),
            option(3, false, false, 5, new RideDistanceTask(EntityTypes.HORSE, "Horse", "🐎", 250, 750, 0.015D), "Ride a Horse", "Travel {count} blocks while riding a horse."),
            option(3, false, false, 5, new RideDistanceTask(EntityTypes.DONKEY, "Donkey", "🫏", 150, 500, 0.02D), "Ride a Donkey", "Travel {count} blocks while riding a donkey."),
            option(2, false, false, 5, new RideDistanceTask(EntityTypes.LLAMA, "Llama", "🦙", 100, 350, 0.025D), "Ride a Llama", "Travel {count} blocks while riding a llama."),
            option(1, false, false, 5, new RideDistanceTask(EntityTypes.SKELETON_HORSE, "Skeleton Horse", "💀", 150, 500, 0.025D), "Ride a Skeleton Horse", "Travel {count} blocks while riding a skeleton horse."),
            option(4, false, false, 5, new RideDistanceTask(EntityTypes.CAMEL, "Camel", "🐫", 150, 500, 0.02D), "Ride a Camel", "Travel {count} blocks while riding a camel."),
            option(2, false, false, 5, new RideDistanceTask(EntityTypes.CAMEL_HUSK, "Camel Husk", "🏜️", 100, 300, 0.035D), "Ride a Camel Husk", "Travel {count} blocks while riding a camel husk."),
            option(3, false, false, 5, new RideDistanceTask(EntityTypes.NAUTILUS, "Nautilus", "🐚", 150, 500, 0.025D), "Ride a Nautilus", "Travel {count} blocks while riding a nautilus."),
            option(4, false, false, 5, new RideDistanceTask(EntityTypes.ZOMBIE_NAUTILUS, "Zombie Nautilus", "🧟", 100, 300, 0.04D), "Ride a Zombie Nautilus", "Travel {count} blocks while riding a zombie nautilus."),
            option(2, false, false, 5, new RideDistanceTask(EntityTypes.OAK_CHEST_BOAT, "Chest Boat", "🛶", 250, 750, 0.015D), "Ride a Chest Boat", "Travel {count} blocks while riding a chest boat."),
            option(4, true, false, 8, new RideDistanceTask(EntityTypes.STRIDER, "Strider", "🟥", 150, 500, 0.025D), "Ride a Strider", "Travel {count} blocks while riding a strider."),
            option(3, true, false, 8, new RideDistanceTask(EntityTypes.HAPPY_GHAST, "Happy Ghast", "😊", 250, 750, 0.02D), "Ride a Happy Ghast", "Travel {count} blocks while riding a happy ghast."),
            option(3, false, false, 5, simple(DailySimpleEvent.MILK_COW, "Fresh Milk", "Milk {count} cows.", "🥛", 1, 3, 2.0D, "Milked", "cows"), "Fresh Milk", "Milk {count} cows."),
            option(3, false, false, 5, simple(DailySimpleEvent.BRUSH_ARMADILLO, "Brush an Armadillo", "Brush {count} armadillos.", "🪥", 1, 3, 2.0D, "Brushed", "armadillos"), "Brush an Armadillo", "Brush {count} armadillos."),

            // Weapon challenges.
            option(2, false, false, 5, new KillWithItemTask(Items.CROSSBOW, "a Crossbow", 1, 3, 3.0D), "Defeat a Mob with a Crossbow", "Defeat {count} mobs using a crossbow."),
            option(2, false, false, 5, new KillWithItemTask(ItemTags.SPEARS, "a Spear", 1, 3, 3.0D), "Defeat a Mob with a Spear", "Defeat {count} mobs using a spear."),
            option(2, false, false, 5, new KillWithItemTask(ItemTags.AXES, "an Axe", 1, 3, 3.0D), "Defeat a Mob with an Axe", "Defeat {count} mobs using an axe."),
            option(2, false, false, 5, new KillWithItemTask(ItemTags.SWORDS, "a Sword", 2, 5, 1.5D), "Defeat a Mob with a Sword", "Defeat {count} mobs using a sword."),
            option(2, false, false, 5, new KillWithItemTask(Items.BOW, "a Bow", 1, 3, 3.0D), "Defeat a Mob with a Bow", "Defeat {count} mobs using a bow."),
            option(2, false, false, 5, new KillWithItemTask(Items.MACE, "a Mace", 1, 2, 5.0D), "Defeat a Mob with a Mace", "Defeat {count} mobs using a mace."),
            option(2, false, false, 5, new KillWithItemTask(Items.STICK, "a Stick", 1, 1, 7.0D), "Defeat a Mob with a Stick", "Defeat {count} mobs using a stick."),
            option(2, false, false, 5, new KillWithItemTask(Items.SNOWBALL, "Snowballs", 1, 1, 8.0D), "Defeat a Mob with Snowballs", "Defeat {count} mobs using snowballs."),
            option(2, false, false, 5, new KillWithItemTask(Items.EGG, "Eggs", 1, 1, 8.0D), "Defeat a Mob with Eggs", "Defeat {count} mobs using eggs."),
            option(2, false, false, 5, new KillWithItemTask(Items.FEATHER, "a Feather", 1, 1, 7.0D), "Defeat a Mob with a Feather", "Defeat {count} mobs using a feather."),
            option(2, false, false, 5, new KillWithItemTask(ItemTags.HOES, "a Hoe", 1, 3, 3.0D), "Defeat a Mob with a Hoe", "Defeat {count} mobs using a hoe."),
            option(2, false, false, 5, new KillWithItemTask(ItemTags.PICKAXES, "a Pickaxe", 1, 3, 3.0D), "Defeat a Mob with a Pickaxe", "Defeat {count} mobs using a pickaxe."),
            option(2, false, false, 5, new KillWithItemTask(Items.TRIDENT, "a Trident", 1, 4, 2.0D), "Defeat a Mob with a Trident", "Defeat {count} mobs using a trident."),
            option(2, false, false, 5, new KillWithItemTask(Items.FIREWORK_ROCKET, "Fireworks", 1, 2, 5.0D), "Defeat a Mob with Fireworks", "Defeat {count} mobs using fireworks."),
            option(1, false, false, 5, new HitPlayerWithProjectileTask(EntityTypes.SNOWBALL, "Snowballs"), "Snowballs Fight", "Hit another player with snowballs {count} times."),
            option(1, false, false, 5, new HitPlayerWithProjectileTask(EntityTypes.EGG, "Eggs"), "Eggs Fight", "Hit another player with eggs {count} times."),

            // Items, crops, and decoration
            option(2, false, false, 5, new UseItemTask(Items.GOAT_HORN, "a Goat Horn", "📯", 1, 3, 0.75D), "Use a Goat Horn", "Use a goat horn {count} times."),
            option(3, false, false, 5, simple(DailySimpleEvent.PLAY_MUSIC_DISC, "Play Music Discs", "Put {count} music discs in a jukebox.", "💿", 1, 3, 0.75D, "Played", "discs"), "Play Music Discs", "Put {count} music discs in a jukebox."),
            option(3, false, false, 5, new PlantCropTask(Items.BEETROOT_SEEDS, "Beetroot Seeds", "🌱", 8, 18, 0.4D), "Plant Beetroot Seeds", "Plant {count} beetroot seeds."),
            option(2, false, false, 5, new PlantCropTask(Items.WHEAT_SEEDS, "Wheat Seeds", "🌾", 10, 24, 0.3D), "Plant Wheat Seeds", "Plant {count} wheat seeds."),
            option(3, false, false, 5, new PlantCropTask(Items.CARROT, "Carrots", "🥕", 8, 20, 0.35D), "Plant Carrots", "Plant {count} carrots."),
            option(3, false, false, 5, new PlantCropTask(Items.POTATO, "Potatoes", "🥔", 8, 20, 0.35D), "Plant Potatoes", "Plant {count} potatoes."),
            option(3, false, false, 5, new PlantCropTask(Items.PUMPKIN_SEEDS, "Pumpkin Seeds", "🎃", 6, 16, 0.45D), "Plant Pumpkin Seeds", "Plant {count} pumpkin seeds."),
            option(3, false, false, 5, new PlantCropTask(Items.MELON_SEEDS, "Melon Seeds", "🍉", 6, 16, 0.45D), "Plant Melon Seeds", "Plant {count} melon seeds."),
            option(4, false, false, 5, new PlantCropTask(Items.TORCHFLOWER_SEEDS, "Torchflower Seeds", "🌼", 2, 6, 1.25D), "Plant Torchflower Seeds", "Plant {count} torchflower seeds."),
            option(4, false, false, 5, new PlantCropTask(Items.PITCHER_POD, "Pitcher Pods", "🪻", 2, 6, 1.25D), "Plant Pitcher Pods", "Plant {count} pitcher pods."),
            option(4, false, false, 5, new PlantCropTask(Items.COCOA_BEANS, "Cocoa", "🍫", 4, 12, 0.6D), "Plant Cocoa", "Plant {count} cocoa."),
            option(2, false, false, 5, new PlantCropTask(Items.SUGAR_CANE, "Sugar Cane", "🎋", 8, 20, 0.35D), "Plant Sugar Cane", "Plant {count} sugar cane."),
            option(3, true, false, 8, new PlantCropTask(Items.NETHER_WART, "Nether Wart", "🔴", 6, 16, 0.5D), "Plant Nether Wart", "Plant {count} nether wart."),
            option(2, false, false, 5, new CreateGolemTask(EntityTypes.COPPER_GOLEM, "New Copper on the Block", "Be the closest player when a copper golem is created.", "🟠", 10.0D), "New Copper on the Block", "Be the closest player when a copper golem is created."),
            option(2, false, false, 5, new CreateGolemTask(EntityTypes.IRON_GOLEM, "Ironclad Welcome", "Be the closest player when an iron golem is created.", "🤖", 12.0D), "Ironclad Welcome", "Be the closest player when an iron golem is created."),
            option(2, false, false, 5, new CreateGolemTask(EntityTypes.SNOW_GOLEM, "A Warm Welcome", "Be the closest player when a snow golem is created. Keep it away from the fireplace.", "⛄", 6.0D), "A Warm Welcome", "Be the closest player when a snow golem is created. Keep it away from the fireplace."),
            option(3, false, false, 5, simple(DailySimpleEvent.RING_BELL, "Ring a Bell", "Ring a bell {count} times. Let the neighbourhood know you found it.", "🔔", 3, 8, 0.75D, "Rang", "times"), "Ring a Bell", "Ring a bell {count} times. Let the neighbourhood know you found it."),

            // Damage
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.MAGIC, "Magic", "🪄", 5.0D), "Get Hurt by Magic", "Take damage from magic without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.FALLING_ANVIL, "a Falling Anvil", "⚒️", 7.0D), "Get Hurt by a Falling Anvil", "Take damage from a falling anvil without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.SWEET_BERRY_BUSH, "a Sweet Berry Bush", "🫐", 4.0D), "Get Hurt by a Sweet Berry Bush", "Take damage from a sweet berry bush without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.CACTUS, "a Cactus", "🌵", 4.0D), "Get Hurt by a Cactus", "Take damage from a cactus without dying."),
            option(1, false, false, 5, new TakeDamageTask(DamageTypes.LIGHTNING_BOLT, "Lightning", "⚡", 10.0D), "Get Hurt by Lightning", "Take damage from lightning without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.WITCH, "a Witch's Harming Potion", "🧙", 8.0D), "Get Hurt by a Witch's Harming Potion", "Take damage from a witch's harming potion without dying."),
            option(1, false, false, 5, new TakeDamageTask(DamageTypes.DROWN, "Drowning", "🌊", 5.0D), "Get Hurt by Drowning", "Take damage from drowning without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.ENDER_PEARL, "an Ender Pearl", "🟢", 4.0D), "Get Hurt by an Ender Pearl", "Take damage from an ender pearl without dying."),
            option(1, false, false, 5, new TakeDamageTask(DamageTypes.FALL, "a Fall", "🪂", 4.0D), "Get Hurt by a Fall", "Take damage from a fall without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.FIREWORKS, "Fireworks", "🎆", 6.0D), "Get Hurt by Fireworks", "Take damage from fireworks without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.FALLING_STALACTITE, "a Falling Stalactite (the downward-pointing ones, pretty sure...)", "🪨", 7.0D), "Get Hurt by a Falling Stalactite (the downward-pointing ones, pretty sure...)", "Take damage from a falling stalactite (the downward-pointing ones, pretty sure...) without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.STALAGMITE, "a Stalagmite (the upward-pointing one, I think...)", "📍", 6.0D), "Get Hurt by a Stalagmite (the upward-pointing one, I think...)", "Take damage from a stalagmite (the upward-pointing one, i think...) without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.IN_WALL, "Suffocation", "🧱", 5.0D), "Get Hurt by Suffocation", "Take damage from suffocation without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.STARVE, "Starvation", "🍽️", 6.0D), "Get Hurt by Starvation", "Take damage from starvation without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.STING, "a Bee Sting", "🐝", 5.0D), "Get Hurt by a Bee Sting", "Take damage from a bee sting without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.SPIT, "Llama Spit", "🦙", 5.0D), "Get Hurt by Llama Spit", "Take damage from llama spit without dying."),
            option(4, false, false, 5, new TakeDamageTask(DamageTypes.SONIC_BOOM, "a Sonic Boom", "📡", 10.0D), "Get Hurt by a Sonic Boom", "Take damage from a sonic boom without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.MACE_SMASH, "a Mace Smash", "🔨", 7.0D), "Get Hurt by a Mace Smash", "Take damage from a mace smash without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.SPEAR, "a Spear", "🗡️", 6.0D), "Get Hurt by a Spear", "Take damage from a spear without dying."),
            option(3, false, false, 5, new TakeDamageTask(DamageTypes.FREEZE, "Freezing", "🥶", 5.0D), "Get Hurt by Freezing", "Take damage from freezing without dying."),
            option(2, true, false, 8, new TakeDamageTask(DamageTypes.HOT_FLOOR, "a Magma Block", "🔥", 5.0D), "Get Hurt by a Magma Block", "Take damage from a magma block without dying."),
            option(1, false, false, 5, new TakeDamageTask(DamageTypes.CAMPFIRE, "a Campfire", "🏕️", 4.0D), "Get Hurt by a Campfire", "Take damage from a campfire without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.THORNS, "the Thorns enchantment", "🌹", 5.0D), "Get Hurt by the Thorns enchantment", "Take damage from the thorns enchantment without dying."),
            option(2, false, false, 5, new TakeDamageTask(DamageTypes.WIND_CHARGE, "a Wind Charge", "🌬️", 5.0D), "Get Hurt by a Wind Charge", "Take damage from a wind charge without dying."),
            option(2, true, false, 8, new TakeDamageTask(DamageTypes.FIREBALL, "a Fireball", "🔥", 6.0D), "Get Hurt by a Fireball", "Take damage from a fireball without dying."),
            option(4, false, false, 5, new TakeDamageTask(DamageTypes.SULFUR_CUBE_HOT, "a Hot Sulfur Cube", "🟨", 6.0D), "Get Hurt by a Hot Sulfur Cube", "Take damage from a hot sulfur cube without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.WARDEN, "the Warden (you don't technically have to die to complete this but... well... you know the warden... let's see how you do. :))", "📡", 10.0D), "Get Hurt by the Warden (you don't technically have to die to complete this but... well... you know the warden... let's see how you do. :))", "Take damage from the warden (you don't technically have to die to complete this but... well... you know the warden... let's see how you do. :)) without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.GOAT, "a Goat's Ram", "🐐", 6.0D), "Get Hurt by a Goat's Ram", "Take damage from a goat's ram without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.PUFFERFISH, "a Pufferfish", "🐡", 5.0D), "Get Hurt by a Pufferfish", "Take damage from a pufferfish without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.GUARDIAN, "a Guardian", "🔱", 7.0D), "Get Hurt by a Guardian", "Take damage from a guardian without dying."),
            option(1, false, false, 5, new TakeDamageTask(EntityTypes.ELDER_GUARDIAN, "an Elder Guardian", "🐟", 9.0D), "Get Hurt by an Elder Guardian", "Take damage from an elder guardian without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.EVOKER, "an Evoker", "🪄", 8.0D), "Get Hurt by an Evoker", "Take damage from an evoker without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.RAVAGER, "a Ravager", "🐂", 7.0D), "Get Hurt by a Ravager", "Take damage from a ravager without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.BREEZE, "a Breeze", "🌬️", 6.0D), "Get Hurt by a Breeze", "Take damage from a breeze without dying."),
            option(2, false, true, 10, new TakeDamageTask(EntityTypes.SHULKER, "a Shulker", "📦", 8.0D), "Get Hurt by a Shulker", "Take damage from a shulker without dying."),
            option(4, false, false, 5, new TakeDamageTask(EntityTypes.PHANTOM, "a Phantom", "🌙", 6.0D), "Get Hurt by a Phantom", "Take damage from a phantom without dying."),
            option(3, false, false, 5, new TakeDamageTask(EntityTypes.DOLPHIN, "an Angry Dolphin", "🐬", 6.0D), "Get Hurt by an Angry Dolphin", "Take damage from an angry dolphin without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.POLAR_BEAR, "a Polar Bear", "🐻‍❄️", 7.0D), "Get Hurt by a Polar Bear", "Take damage from a polar bear without dying."),
            option(2, false, false, 5, new TakeDamageTask(EntityTypes.TRADER_LLAMA, "a Trader Llama", "🦙", 6.0D), "Get Hurt by a Trader Llama", "Take damage from a trader llama without dying."),
            option(4, false, false, 5, new TakeDamageTask(EntityTypes.VEX, "a Vex", "🪽", 7.0D), "Get Hurt by a Vex", "Take damage from a vex without dying."),
            option(3, false, false, 5, new TakeDamageTask(EntityTypes.ENDERMAN, "an Angry Enderman", "👁️", 6.0D), "Get Hurt by an Angry Enderman", "Take damage from an angry enderman without dying."),
            option(3, false, false, 5, new TakeDamageTask(EntityTypes.PARCHED, "a Parched", "🏜️", 7.0D), "Get Hurt by a Parched", "Take damage from a parched without dying."),
            option(4, true, false, 8, new TakeDamageTask(EntityTypes.PIGLIN_BRUTE, "a Piglin Brute", "🪓", 7.0D), "Get Hurt by a Piglin Brute", "Take damage from a piglin brute without dying."),
            option(2, true, false, 8, new TakeDamageTask(EntityTypes.HOGLIN, "a Hoglin", "🐗", 7.0D), "Get Hurt by a Hoglin", "Take damage from a hoglin without dying."),
            option(3, true, false, 8, new TakeDamageTask(EntityTypes.WITHER, "the Wither", "☠️", 10.0D), "Get Hurt by the Wither", "Take damage from the wither without dying."),
            option(3, false, true, 10, new TakeDamageTask(EntityTypes.ENDER_DRAGON, "the Ender Dragon", "🐉", 12.0D), "Get Hurt by the Ender Dragon", "Take damage from the ender dragon without dying."),

            // Block and decoration interactions
            option(4, false, false, 5, simple(DailySimpleEvent.FILL_FLOWER_POT, "Pot Some Flowers", "Put {count} flowers in flower pots. Give an empty corner some colour.", "🌷", 2, 5, 1.25D, "Potted", "flowers"), "Pot Some Flowers", "Put {count} flowers in flower pots. Give an empty corner some colour."),
            option(4, false, false, 5, simple(DailySimpleEvent.HANG_PAINTING, "Curate a Wall", "Hang {count} paintings. Find one which suits the room.", "🖼️", 1, 4, 1.5D, "Hung", "paintings"), "Curate a Wall", "Hang {count} paintings. Find one which suits the room."),
            option(4, false, false, 5, simple(DailySimpleEvent.FILL_BOOKSHELF, "Stock a Bookshelf", "Put {count} books in chiseled bookshelves. A library starts with one shelf.", "📚", 2, 6, 1.0D, "Stored", "books"), "Stock a Bookshelf", "Put {count} books in chiseled bookshelves. A library starts with one shelf."),
            option(3, false, false, 5, simple(DailySimpleEvent.READ_NEW_JOKE, "Fresh Material", "Read a joke book you haven't read before.", "😂", 1, 1, 5.0D, "Read", "joke"), "Fresh Material", "Read a joke book you haven't read before."),
            option(3, false, false, 5, simple(DailySimpleEvent.KICK_SULFUR_CUBE, "Kick a Sulfur Cube", "Kick a sulfur cube. It probably had it coming.", "🟨", 1, 1, 5.0D, "Kicked", "cube"), "Kick a Sulfur Cube", "Kick a sulfur cube. It probably had it coming."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.BASEDRUM, "Bass Drum"), "Bass Drum Notes", "Right-click a note block with the bass drum instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.HARP, "Harp"), "Harp Notes", "Right-click a note block with the harp instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.SNARE, "Snare"), "Snare Notes", "Right-click a note block with the snare instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.FLUTE, "Flute"), "Flute Notes", "Right-click a note block with the flute instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.BELL, "Bell"), "Bell Notes", "Right-click a note block with the bell instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.GUITAR, "Guitar"), "Guitar Notes", "Right-click a note block with the guitar instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.XYLOPHONE, "Xylophone"), "Xylophone Notes", "Right-click a note block with the xylophone instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.COW_BELL, "Cow Bell"), "Cow Bell Notes", "Right-click a note block with the cow bell instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.DIDGERIDOO, "Didgeridoo"), "Didgeridoo Notes", "Right-click a note block with the didgeridoo instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.BANJO, "Banjo"), "Banjo Notes", "Right-click a note block with the banjo instrument {count} times."),
            option(2, false, false, 5, new PlayNoteBlockTask(NoteBlockInstrument.TRUMPET, "Copper Trumpet"), "Copper Trumpet Notes", "Right-click a note block with the copper trumpet instrument {count} times."),
            option(4, false, false, 5, simple(DailySimpleEvent.CUSTOMIZE_BANNER, "Banner Workshop", "Apply {count} patterns to banners. Make something worth hanging up.", "🚩", 3, 8, 1.0D, "Applied", "patterns"), "Banner Workshop", "Apply {count} patterns to banners. Make something worth hanging up."),
            option(3, false, false, 5, new UseBlockTask(Blocks.FLETCHING_TABLE, "Fletching Table", "🏹", 4, 12, 0.75D), "Use a Fletching Table", "Right-click a fletching table {count} times."),
            option(3, false, false, 5, simple(DailySimpleEvent.EYE_CONTACT_ENDERMAN, "A Dangerous Look", "Make eye contact with an Enderman. Apologise quickly.", "👁️", 1, 1, 5.0D, "Made", "eye contact"), "A Dangerous Look", "Make eye contact with an Enderman. Apologise quickly."),
            option(3, false, false, 5, simple(DailySimpleEvent.MODIFY_ITEM_FRAME, "Improve an Item Frame", "Make an item frame invisible or glowing and give an item a proper display.", "🖼️", 1, 1, 4.0D, "Improved", "frame"), "Improve an Item Frame", "Make an item frame invisible or glowing and give an item a proper display."),

            // Crafting, activity, and curing
            option(2, false, false, 5, new CraftItemTask(Items.CLOCK, "Clocks", "🕰️", 1, 3, 2.0D), "Craft Clocks", "Craft {count} clocks."),
            option(4, false, false, 5, new CraftItemTask(Items.GOLDEN_DANDELION, "Golden Dandelions", "🌼", 1, 10, 2.0D), "Craft Golden Dandelions", "Craft {count} golden dandelions."),
            option(3, false, false, 5, new CraftItemTask(Items.SPYGLASS, "Spyglasses", "🔭", 1, 2, 3.0D), "Craft Spyglasses", "Craft {count} spyglasses."),
            option(2, false, false, 5, new CraftItemTask(Items.CONCRETE_POWDER.blue(), "Blue Concrete Powder", "🔵", 8, 24, 0.35D), "Craft Blue Concrete Powder", "Craft {count} blue concrete powder."),
            option(3, false, false, 5, new CraftItemTask(Items.DAYLIGHT_DETECTOR, "Daylight Detectors", "☀️", 1, 3, 3.0D), "Craft Daylight Detectors", "Craft {count} daylight detectors."),
            option(3, false, false, 5, new CraftItemTask(Items.STICKY_PISTON, "Sticky Pistons", "🟩", 1, 3, 2.0D), "Craft Sticky Pistons", "Craft {count} sticky pistons."),
            option(3, false, false, 5, new CraftItemTask(Items.PUMPKIN_PIE, "Pumpkin Pies", "🥧", 2, 5, 1.75D), "Craft Pumpkin Pies", "Craft {count} pumpkin pies."),
            option(3, false, false, 5, new CraftItemTask(Items.NOTE_BLOCK, "Note Blocks", "🎵", 1, 3, 3.0D), "Craft Note Blocks", "Craft {count} note blocks."),
            option(3, false, false, 5, new CraftItemTask(Items.TRAPPED_CHEST, "Trapped Chests", "📦", 1, 3, 3.0D), "Craft Trapped Chests", "Craft {count} trapped chests."),
            option(3, false, false, 5, new CraftItemTask(Items.BANNER.blue(), "Blue Banners", "🚩", 1, 3, 3.0D), "Craft Blue Banners", "Craft {count} blue banners."),
            option(3, false, false, 5, new CraftItemTask(Items.DECORATED_POT, "Decorated Pots", "🏺", 1, 2, 5.0D), "Craft Decorated Pots", "Craft {count} decorated pots."),
            option(3, false, false, 5, new CraftItemTask(Items.TARGET, "Target Blocks", "🎯", 1, 3, 3.0D), "Craft Target Blocks", "Craft {count} target blocks."),
            option(3, false, false, 5, new CraftItemTask(Items.COMPARATOR, "Comparators", "🔴", 1, 3, 3.0D), "Craft Comparators", "Craft {count} comparators."),
            option(3, false, false, 5, new CraftItemTask(Items.OBSERVER, "Observers", "👁️", 1, 3, 3.0D), "Craft Observers", "Craft {count} observers."),
            option(3, false, false, 5, new CraftItemTask(Items.DISPENSER, "Dispensers", "🏹", 1, 3, 3.0D), "Craft Dispensers", "Craft {count} dispensers."),
            option(3, false, false, 5, new CraftItemTask(Items.ARMOR_STAND, "Armor Stands", "🛡️", 1, 3, 3.0D), "Craft Armor Stands", "Craft {count} armor stands."),
            option(3, false, false, 5, new CraftItemTask(Items.LOOM, "Looms", "🧶", 1, 3, 2.0D), "Craft Looms", "Craft {count} looms."),
            option(3, false, false, 5, new CraftItemTask(Items.CARTOGRAPHY_TABLE, "Cartography Tables", "🗺️", 1, 3, 2.0D), "Craft Cartography Tables", "Craft {count} cartography tables."),
            option(3, false, false, 5, new CraftItemTask(Items.CAMPFIRE, "Campfires", "🏕️", 1, 4, 2.0D), "Craft Campfires", "Craft {count} campfires."),
            option(3, false, false, 5, new CraftItemTask(Items.SCAFFOLDING, "Scaffolding", "🏗️", 8, 100, 0.35D), "Craft Scaffolding", "Craft {count} scaffolding."),
            option(3, false, false, 5, new CraftItemTask(Items.FIREWORK_STAR, "Firework Stars", "🎆", 2, 8, 1.0D), "Craft Firework Stars", "Craft {count} firework stars."),
            option(2, false, false, 5, new CraftItemTask(Items.RECOVERY_COMPASS, "Recovery Compasses", "🧭", 1, 1, 9.0D), "Craft Recovery Compasses", "Craft {count} recovery compasses."),
            option(3, false, false, 5, new CraftItemTask(Items.BRUSH, "Brushes", "🖌️", 1, 4, 2.0D), "Craft Brushes", "Craft {count} brushes."),
            option(3, false, false, 5, new PlayTimeTask(), "Stay a While", "Play on the server for at least {count} minutes today."),
            option(1, true, false, 8, new CureZombieVillagerTask(), "Zombie Doctor", "Cure {count} zombie villagers.")
    );

    private DailyTaskRegistry() {
    }

    public static void validate() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Class<?>> usedFamilies = new HashSet<>();
        for (Option option : TASKS) {
            DailyTaskDefinition definition = option.definition();
            usedFamilies.add(definition.getClass());
            if (!ids.add(definition.getId())) {
                throw new IllegalStateException("Duplicate daily task id: " + definition.getId());
            }
            JsonObject task = option.create(new Random(definition.getId().hashCode()));
            int instances = task.get("max").getAsInt();
            if (instances < 1) instances = task.get("requiredCount").getAsInt();
            DailyTaskAmount amount = new DailyTaskAmount(
                    task.get("baseCost").getAsInt(),
                    task.get("rewardPerIteration").getAsDouble()
            );
            if (!task.get("id").getAsString().equals(definition.getId())
                    || task.get("name").getAsString().isBlank()
                    || !names.add(task.get("name").getAsString())
                    || task.get("description").getAsString().isBlank()
                    || task.get("emoji").getAsString().isBlank()
                    || task.get("current").getAsInt() != 0
                    || task.get("max").getAsInt() == 0
                    || task.get("baseCost").getAsInt() != option.baseCost()
                    || task.get("rewardDabloons").getAsInt() != amount.reward(instances)
                    || task.get("rewardDabloons").getAsInt() != definition.getReward(task)) {
                throw new IllegalStateException("Invalid daily task definition: " + definition.getId());
            }
        }
        if (!usedFamilies.equals(FAMILY_WEIGHTS.keySet())) {
            throw new IllegalStateException("Daily task family weights do not match the catalog");
        }
    }

    public static List<JsonObject> pick(String seed, int count, Collection<String> excludedIds) {
        List<Option> available = new ArrayList<>(TASKS.stream()
                .filter(option -> !excludedIds.contains(option.definition().getId()))
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
                            result.add(variant.create(random));
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
        int baseCost = task.get("baseCost").getAsInt();
        if (find(id) == null || current < 0 || reward < 0 || baseCost < 0 || max == 0 || max < -1 || (max > 0 && current > max)) {
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

    private static Option option(
            int weight,
            boolean nether,
            boolean end,
            int baseCost,
            DailyTaskDefinition definition,
            String name,
            String description
    ) {
        return new Option(weight, nether, end, baseCost, name, description, definition);
    }

    private record Option(
            int weight,
            boolean nether,
            boolean end,
            int baseCost,
            String name,
            String description,
            DailyTaskDefinition definition
    ) {
        private Option {
            if (weight < 1) throw new IllegalArgumentException("Daily task weights must be positive");
            if (baseCost < 0) throw new IllegalArgumentException("Daily task base costs must not be negative");
            if (name.isBlank() || description.isBlank()) throw new IllegalArgumentException("Daily task copy must not be blank");
        }

        private JsonObject create(Random random) {
            JsonObject task = definition.create(random, name, description);
            int instances = task.get("max").getAsInt();
            if (instances < 1) instances = task.get("requiredCount").getAsInt();

            double oldRate = task.get("rewardPerIteration").getAsDouble();
            DailyTaskAmount amount = new DailyTaskAmount(baseCost, oldRate / 2.0D);
            task.addProperty("baseCost", amount.baseCost());
            task.addProperty("rewardPerIteration", amount.perInstance());
            task.addProperty("rewardDabloons", amount.reward(instances));
            return task;
        }
    }
}
