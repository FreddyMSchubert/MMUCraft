package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GetKnowledgeTipResponse;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class KnowledgeTips {
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final ZoneId MANCHESTER_TIME_ZONE = ZoneId.of("Europe/London");
    private static final Map<UUID, String> LAST_GREETING = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_FLAVOUR = new ConcurrentHashMap<>();

    private static final List<WeightedMessage> GREETINGS = List.of(
            message(10, "Welcome back!", context -> true),
            message(3, "Oh hey, Welcome back!", context -> true),
            message(5, "Greetings!", context -> true),
            message(1, "Greetings, individual with great taste in video games!", context -> true),
            message(4, "Glad youre back!", context -> true),
            message(4, "You're back! Hi!", context -> true),
            message(4, "Welcome, welcome!", context -> true),
            message(2, "Hey, Welcome back!", context -> true),
            message(4, "Good to see you!", context -> true),
            message(4, "Happy to have you!", context -> true),
            message(5, "Hey again.", context -> true),
            message(1, "Hello there. (General Kenobi)", context -> true),
            message(6, "Hello again.", context -> true),
            message(4, "Look who's back!", context -> true),
            message(5, "Good to see you!", context -> true),
            message(3, "Hey there!", context -> true),
            message(3, "Always nice to see you!", context -> true),
            message(2, "Fancy seeing you here!", context -> true),
            message(2, "Good to see a familiar face!", context -> true),
            message(3, "Hello there!", context -> true),
            message(1, "Welcome back in the cubicle!", context -> true),

            message(25, "Back already?", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(25, "Missed the server already?", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Welcome back, that was quick!", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Hey, you again!", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Right back at it.", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "That was fast.", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Picking up where we left off?", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Ready for round two?", context -> context.recentlyPlayed(15L * MINUTE_MS)),

            message(100, "Long time no see!", context -> context.goneFor(7L * DAY_MS)),
            message(100, "Feels like it's been a minute.", context -> context.goneFor(7L * DAY_MS)),
            message(100, "Glad you're back around.", context -> context.goneFor(7L * DAY_MS)),
            message(100, "Missed you!", context -> context.goneFor(7L * DAY_MS)),
            message(10000, "Welcome home!", context -> context.goneFor(30L * DAY_MS)),
            message(10000, "Look who's finally back!", context -> context.goneFor(30L * DAY_MS)),
            message(10000, "Nice of you to make a comeback.", context -> context.goneFor(30L * DAY_MS)),
            message(10000, "Finally! Feels like forever since you were here.", context -> context.goneFor(30L * DAY_MS)),
            message(10000, "It's been ages — welcome back!", context -> context.goneFor(30L * DAY_MS)),
            message(1000000, "You do still exist!", context -> context.goneFor(90L * DAY_MS)),

            message(25, "Hello, night owl!", context -> context.hour() < 5),
            message(30, "Hiya, couldn't sleep?", context -> context.hour() < 5),
            message(20, "Still awake?", context -> context.hour() < 5),
            message(18, "Good Morning!", context -> context.hour() >= 5 && context.hour() < 8),
            message(18, "You're up early.", context -> context.hour() >= 5 && context.hour() < 8),
            message(25, "Good Morning, early bird!", context -> context.hour() >= 5 && context.hour() < 8),
            message(27, "Good morning!", context -> context.hour() >= 8 && context.hour() < 12),
            message(27, "Good afternoon!", context -> context.hour() >= 12 && context.hour() < 18),
            message(27, "Good evening!", context -> context.hour() >= 18),

            message(14, "Happy Monday!", context -> context.day() == DayOfWeek.MONDAY),
            message(14, "Happy Tuesday!", context -> context.day() == DayOfWeek.TUESDAY),
            message(14, "Happy Wednesday!", context -> context.day() == DayOfWeek.WEDNESDAY),
            message(14, "Happy Thursday!", context -> context.day() == DayOfWeek.THURSDAY),
            message(14, "Happy Friday!", context -> context.day() == DayOfWeek.FRIDAY),
            message(20, "Happy Surprising Saturday!", context -> context.day() == DayOfWeek.SATURDAY),
            message(20, "Happy Sunday Funday!", context -> context.day() == DayOfWeek.SUNDAY),

            // New Year
            message(100_000, "Happy New Year! It's lovely to have you back.", context -> context.dateIs(Month.JANUARY, 1)),
            message(100_000, "New year, same wonderful you. Welcome back!", context -> context.dateIs(Month.JANUARY, 1)),
            message(100_000, "Welcome back! Let's make this year a blocky one.", context -> context.dateIs(Month.JANUARY, 1)),
            message(10, "Welcome back! Fancy one last adventure before the year ends?", context -> context.withinDaysBefore(LocalDate.of(context.year() + 1, Month.JANUARY, 1), 3)),
            message(10, "Good to see you! Ready to build your way into the New Year?", context -> context.withinDaysBefore(LocalDate.of(context.year() + 1, Month.JANUARY, 1), 3)),
            message(100_000, "Happy New Year's Eve! Lovely to see you before the clock strikes twelve.", context -> context.dateIs(Month.DECEMBER, 31)),
            message(100_000, "Welcome back! One last Minecraft session for the year?", context -> context.dateIs(Month.DECEMBER, 31)),

            // Valentine's Day
            message(100_000, "Happy Valentine's Day! The server is better with you here.", context -> context.dateIs(Month.FEBRUARY, 14)),
            message(100_000, "Welcome back, Valentine! We saved you a spot.", context -> context.dateIs(Month.FEBRUARY, 14)),
            message(100_000, "Roses are red, diamonds are blue. Welcome back! We missed you.", context -> context.dateIs(Month.FEBRUARY, 14)),
            message(10, "Love is in the air, and look who's back!", context -> context.withinDaysBefore(LocalDate.of(context.year(), Month.FEBRUARY, 14), 3)),

            // Pancake Day and the patron saints
            message(100_000, "Happy Pancake Day! Stack them high, then stack some blocks.", context -> context.dateIs(easterSunday(context.year()).minusDays(47))),
            message(100_000, "Welcome back! Hope your pancakes are less square than everything here.", context -> context.dateIs(easterSunday(context.year()).minusDays(47))),
            message(10, "Welcome back! The villagers are practising their pancake flips.", context -> context.withinDaysBefore(easterSunday(context.year()).minusDays(47), 2)),
            message(100_000, "Happy St David's Day! Lovely to see you back.", context -> context.dateIs(Month.MARCH, 1)),
            message(100_000, "Croeso'n ôl! Have a brilliant St David's Day.", context -> context.dateIs(Month.MARCH, 1)),
            message(100_000, "Happy St Patrick's Day! May your mining be lucky.", context -> context.dateIs(Month.MARCH, 17)),
            message(100_000, "Welcome back! The luck of the Irish brought you our way.", context -> context.dateIs(Month.MARCH, 17)),
            message(100_000, "No joke: we're glad you're back. Happy April Fools' Day!", context -> context.dateIs(Month.APRIL, 1)),
            message(100_000, "Welcome back! Everything is completely normal today. Probably.", context -> context.dateIs(Month.APRIL, 1)),
            message(100_000, "Happy St George's Day! Welcome back, dragon slayer.", context -> context.dateIs(Month.APRIL, 23)),
            message(100_000, "Welcome back! Keep an eye out for dragons today.", context -> context.dateIs(Month.APRIL, 23)),
            message(100_000, "Happy St Andrew's Day! A warm welcome back to you.", context -> context.dateIs(Month.NOVEMBER, 30)),
            message(100_000, "Welcome back! Have a brilliant St Andrew's Day.", context -> context.dateIs(Month.NOVEMBER, 30)),

            // Mother's Day, Easter, and Father's Day
            message(100_000, "Happy Mother's Day! Take it easy and enjoy the server.", context -> context.dateIs(easterSunday(context.year()).minusWeeks(3))),
            message(100_000, "Welcome back! Here's to all the brilliant mums and mother figures.", context -> context.dateIs(easterSunday(context.year()).minusWeeks(3))),
            message(100_000, "Welcome back! Have a peaceful Good Friday.", context -> context.dateIs(easterSunday(context.year()).minusDays(2))),
            message(100_000, "Lovely to see you. Hope you're having a good Good Friday.", context -> context.dateIs(easterSunday(context.year()).minusDays(2))),
            message(100_000, "Happy Easter! Welcome back, you good egg.", context -> context.dateIs(easterSunday(context.year()))),
            message(100_000, "Welcome back! Hope the Easter bunny brought diamonds.", context -> context.dateIs(easterSunday(context.year()))),
            message(100_000, "Happy Easter! Time for eggs, adventures, and suspiciously square rabbits.", context -> context.dateIs(easterSunday(context.year()))),
            message(10, "Welcome back! Easter's nearly here. Save room for chocolate.", context -> context.withinDaysBefore(easterSunday(context.year()), 3)),
            message(10, "The Easter bunny has not found the server yet. Good to see you, though!", context -> context.withinDaysBefore(easterSunday(context.year()), 3)),
            message(100_000, "Happy Easter Monday! Welcome back for one more day of chocolate.", context -> context.dateIs(easterSunday(context.year()).plusDays(1))),
            message(100_000, "Welcome back! Bank holiday Minecraft is the best kind.", context -> context.dateIs(easterSunday(context.year()).plusDays(1))),
            message(100_000, "Happy Father's Day! Welcome back.", context -> context.dateIs(firstWeekday(context.year(), Month.JUNE, DayOfWeek.SUNDAY).plusWeeks(2))),
            message(100_000, "Welcome back! Here's to all the brilliant dads and father figures.", context -> context.dateIs(firstWeekday(context.year(), Month.JUNE, DayOfWeek.SUNDAY).plusWeeks(2))),

            // Spring and summer bank holidays
            message(100_000, "Happy early May bank holiday! Glad you're spending a bit of it with us.", context -> context.dateIs(firstWeekday(context.year(), Month.MAY, DayOfWeek.MONDAY))),
            message(100_000, "No alarms, no lectures, just blocks. Welcome back!", context -> context.dateIs(firstWeekday(context.year(), Month.MAY, DayOfWeek.MONDAY))),
            message(100_000, "Welcome back. On VE Day, we remember those who gave us peace.", context -> context.dateIs(Month.MAY, 8)),
            message(100_000, "It's good to have you here. Wishing everyone a thoughtful VE Day.", context -> context.dateIs(Month.MAY, 8)),
            message(100_000, "Happy spring bank holiday! Lovely to see you.", context -> context.dateIs(lastWeekday(context.year(), Month.MAY, DayOfWeek.MONDAY))),
            message(100_000, "Welcome back! Long weekend, long mining session?", context -> context.dateIs(lastWeekday(context.year(), Month.MAY, DayOfWeek.MONDAY))),
            message(100_000, "Happy summer bank holiday! Welcome back.", context -> context.dateIs(lastWeekday(context.year(), Month.AUGUST, DayOfWeek.MONDAY))),
            message(100_000, "Long weekend, big builds. Lovely to see you!", context -> context.dateIs(lastWeekday(context.year(), Month.AUGUST, DayOfWeek.MONDAY))),

            // Manchester Pride weekend
            message(100_000, "Happy Manchester Pride! Be loud, be proud, be yourself.", Context::isManchesterPrideWeekend),
            message(100_000, "Welcome back! The server is brighter with every colour of you.", Context::isManchesterPrideWeekend),
            message(100_000, "Pride weekend in Manchester! Lovely to have you here.", Context::isManchesterPrideWeekend),
            message(10, "Welcome back! Manchester is getting ready to glow for Pride.", context -> context.withinDaysBefore(context.manchesterPrideWeekendStart(), 3)),
            message(10, "Good to see you! The bees are polishing their rainbow stripes.", context -> context.withinDaysBefore(context.manchesterPrideWeekendStart(), 3)),

            // Autumn
            message(100_000, "Happy Halloween! Welcome back, you absolute creeper.", context -> context.dateIs(Month.OCTOBER, 31)),
            message(100_000, "Welcome back! Do not turn around. Just kidding. Probably.", context -> context.dateIs(Month.OCTOBER, 31)),
            message(100_000, "Happy Halloween! Come in. We've been expecting you.", context -> context.dateIs(Month.OCTOBER, 31)),
            message(10, "Welcome back! Things are getting spooky around here.", context -> context.withinDaysBefore(LocalDate.of(context.year(), Month.OCTOBER, 31), 3)),
            message(10, "Good to see you! The pumpkins have been asking about you.", context -> context.withinDaysBefore(LocalDate.of(context.year(), Month.OCTOBER, 31), 3)),
            message(100_000, "Happy Bonfire Night! Welcome back. Mind the sparks.", context -> context.dateIs(Month.NOVEMBER, 5)),
            message(100_000, "Welcome back! The creepers are feeling festive tonight.", context -> context.dateIs(Month.NOVEMBER, 5)),
            message(10, "Welcome back! The sky will be sparkling soon.", context -> context.withinDaysBefore(LocalDate.of(context.year(), Month.NOVEMBER, 5), 2)),
            message(100_000, "Welcome back. Today, we remember those who served.", Context::isRemembranceSunday),
            message(100_000, "It's good to have you here. We will remember them.", Context::isRemembranceSunday),
            message(100_000, "Welcome back. Today, we remember those who served.", context -> context.dateIs(Month.NOVEMBER, 11)),
            message(100_000, "It's good to have you here. We will remember them.", context -> context.dateIs(Month.NOVEMBER, 11)),

            // Christmas
            message(6, "It's beginning to look a lot like Christmas. Welcome back!", Context::isChristmasSeason),
            message(6, "Welcome back! Time to make the world a little more festive.", Context::isChristmasSeason),
            message(6, "Lovely to see you! The server is getting cosy for Christmas.", Context::isChristmasSeason),
            message(10, "Welcome back! Is your Christmas build finished yet?", Context::isChristmasWeek),
            message(10, "Good to see you! Even the creepers are feeling festive.", Context::isChristmasWeek),
            message(100_000, "Merry Christmas Eve! Lovely to have you here.", context -> context.dateIs(Month.DECEMBER, 24)),
            message(100_000, "Welcome back! One more sleep. Make yourself cosy.", context -> context.dateIs(Month.DECEMBER, 24)),
            message(100_000, "Merry Christmas! It's wonderful to see you.", context -> context.dateIs(Month.DECEMBER, 25)),
            message(100_000, "Welcome back, and Merry Christmas from all of MMUCraft!", context -> context.dateIs(Month.DECEMBER, 25)),
            message(100_000, "Merry Christmas! Come in, get cosy, and stay awhile.", context -> context.dateIs(Month.DECEMBER, 25)),
            message(100_000, "Happy Boxing Day! Welcome back for leftovers and blocks.", context -> context.dateIs(Month.DECEMBER, 26)),
            message(100_000, "Welcome back! Perfect day for doing absolutely nothing but Minecraft.", context -> context.dateIs(Month.DECEMBER, 26))
    );

    private static final List<WeightedMessage> FLAVOURS = List.of(
			message(60, "", context -> true), // no flavour text - makes it more special if there is one.

            message(10, "Careful, it's stormy out there.", context -> context.level().isThundering()),
            message(10, "Rough weather out there.", context -> context.level().isThundering()),
            message(10, "It's snowing out.", Context::isSnowing),
            message(10, "Snowy out there.", Context::isSnowing),
            message(10, "Stay dry out there.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Don't forget an umbrella.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Rainy one today.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "It's raining cats and dogs!", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(20, "Come rain or shine, Minecraft time!", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Beautiful day out.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "What a gorgeous day.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "It's a beautiful day today!", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "Hard to complain about that weather.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "Looking bright today.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(5, "The weather is just perfect.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),

            message(5, "Plenty of daylight left.", Context::isDay),
            message(10, "Sunset is close.", Context::isSunset),
            message(6, "Golden hour is coming up.", Context::isSunset),
            message(10, "Catching the sunset?.", Context::isSunset),
            message(14, "Careful out there, it's night.", Context::isNight),

            message(15, "That's quite a view.", context -> context.player().getY() >= 200.0D),
            message(10, "Up in the clouds?", context -> context.player().getY() >= 120.0D),
            message(10, "Thin air up here.", context -> context.player().getY() >= 120.0D),
            message(11, "Back in the mines, huh?", context -> context.player().getY() < 30.0D),
            message(11, "It appears you yearned for the mines.", context -> context.player().getY() < 0.0D),

            message(25, "Full moon. Mind the werewolves.", context -> context.isNight() && context.moonPhase() == MoonPhase.FULL_MOON),
            message(3, "Full moon is coming up soon.", context -> context.isNight() && (context.moonPhase() == MoonPhase.WANING_GIBBOUS || context.moonPhase() == MoonPhase.WAXING_GIBBOUS)),
            message(20, "New moon tonight.", context -> context.isNight() && context.moonPhase() == MoonPhase.NEW_MOON),

            message(22, "Careful, something hostile is nearby!", Context::monstersNearby),
            message(18, "You are not alone.", Context::monstersNearby),
            message(15, "Maybe check behind you.", Context::monstersNearby),
            message(8, "Beware, you have company.", Context::playersNearby),

            message(50, "Watch out, your hearts are low.", context -> context.player().getHealth() <= context.player().getMaxHealth() * 0.5F),
            message(20, "Hungry?", context -> context.player().getFoodData().getFoodLevel() <= 6),
            message(20, "Time for a snack?", context -> context.player().getFoodData().getFoodLevel() <= 6),
            message(10, "Sounds like lunch o'clock.", context -> context.player().getFoodData().getFoodLevel() <= 6),
            message(2000, "I don't want to alarm you... But... You appear to be on fire.", context -> context.player().isOnFire()),
            message(5000, "Oh dang your items are so cooked...", context -> context.player().isInLava()),
            message(300, "Need a blanket? You're shivering.", context -> context.player().isFreezing()),
            message(50, "Quick! You're drowning!", context -> context.player().getAirSupply() < context.player().getMaxAirSupply()),
            message(14, "Bit dark in here.", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(20, "Can't see a thing.", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(20, "Where'd the light go?", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(20, "Hope you brought a torch.", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(20, "Eyes adjusting?", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(20, "Watch your step.", context -> context.level().getMaxLocalRawBrightness(context.player().blockPosition()) < 5),
            message(5000, "AAAAaaahHhHhHhHh!!!", context -> context.player().isFallFlying()),
            message(35, "Nice ride.", context -> context.player().isPassenger()),

            message(20, "Hope you brought fire resistance.", context -> context.level().dimension() == Level.NETHER),
            message(20, "Hope you brought some gold armor.", context -> context.level().dimension() == Level.NETHER),
            message(20, "Careful near the edge.", context -> context.level().dimension() == Level.END),
            message(20, "We're in the endgame now.", context -> context.level().dimension() == Level.END),
            message(3, "Overworld, sweet overworld.", context -> context.level().dimension() == Level.OVERWORLD),
            message(12, "Wow, you have a lot of levels.", context -> context.player().experienceLevel >= 42),
            message(12, "My god, thats a lot of XP!", context -> context.player().experienceLevel >= 60),
            message(20, "Bold choice: no armour.", context -> context.player().getArmorValue() == 0),

            message(3, "Never dig straight down.", context -> true),
            message(2, "Adventure awaits.", context -> true),
            message(2, "What's the plan today?", context -> true),
            message(2, "Your journey continues.", context -> true),
            message(2, "There's more to discover!", context -> true),
            message(2, "The world is yours.", context -> true),
            message(2, "Where to next?", context -> true),
            message(2, "The world is yours.", context -> true),
            message(2, "What will you build today?", context -> true),
            message(2, "Another day in the blocky world.", context -> true),
            message(2, "Creepers like their personal space.", context -> true),
            message(6, "Have you read all your knowledge books?", context -> true),
            message(6, "Have you signed up for the next society events?", context -> true),
            message(6, "Have you checked out your dailies?", context -> true),
            message(6, "Why not try on a different hat today?", context -> true)
    );

    private KnowledgeTips() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tip")
                        .requires(source -> source.getPlayer() != null)
                        .executes(context -> {
                            requestTip(context.getSource().getPlayerOrException(), false, 0L);
                            return 1;
                        })
        ));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 1) {
                player.sendSystemMessage(Component.literal("Welcome to the MMU Minecraft Society!")
                        .withStyle(ChatFormatting.WHITE));
                Announcements.sendUnread(player);
                return;
            }
            PlayerStatsSync.syncNow(player).thenRun(() -> requestTip(
                    player,
                    true,
                    PlayerStatsSync.previousLastPlayedAtUnixMs(player)
            ));
        });
    }

    private static void requestTip(
            ServerPlayer player,
            boolean greet,
            long previousLastPlayedAtUnixMs
    ) {
        GameplayGrpcService.getKnowledgeTip(player.getName().getString(), player.getUUID().toString())
                .thenAccept(response -> send(player, response, greet, previousLastPlayedAtUnixMs))
                .exceptionally(error -> {
                    if (greet) {
                        MainMod.LOGGER.debug("Could not load a join tip for {}", player.getName().getString(), error);
                    }
                    MinecraftServer server = player.level().getServer();
                    if (server != null) server.execute(() -> {
                        if (player.hasDisconnected()) return;
                        if (greet) player.sendSystemMessage(greeting(player, previousLastPlayedAtUnixMs));
                        else player.sendSystemMessage(Component.literal("Tips are taking a nap. Try again.")
                                .withStyle(ChatFormatting.RED));
                        if (greet) Announcements.sendUnread(player);
                    });
                    return null;
                });
    }

    private static void send(
            ServerPlayer player,
            GetKnowledgeTipResponse response,
            boolean greet,
            long previousLastPlayedAtUnixMs
    ) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.execute(() -> {
            if (player.hasDisconnected()) return;
            if (greet) player.sendSystemMessage(greeting(player, previousLastPlayedAtUnixMs));
            if (greet && response.getHasUnreadKnowledge()) {
                player.sendSystemMessage(Component.literal("Warning: You have unread knowledge books. Read them ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(WebsiteCommand.takeMeThere("knowledge", "here", ChatFormatting.RED))
                        .append(Component.literal(".").withStyle(ChatFormatting.GOLD)));
            }
            if (response.getFound()) {
                player.sendSystemMessage(Component.literal("Tip: ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(response.getTip()).withStyle(ChatFormatting.WHITE))
                        .append(" ")
                        .append(WebsiteCommand.takeMeThere(
                                "knowledge/" + response.getKnowledgeId(),
                                "[Read more]",
                                ChatFormatting.GOLD
                        )));
            }
            if (greet) Announcements.sendUnread(player);
        });
    }

    private static Component greeting(ServerPlayer player, long previousLastPlayedAtUnixMs) {
        Context context = context(player, previousLastPlayedAtUnixMs);
        String greeting = pick(GREETINGS, context, LAST_GREETING.get(player.getUUID()));
        String flavour = pick(FLAVOURS, context, LAST_FLAVOUR.get(player.getUUID()));
        LAST_GREETING.put(player.getUUID(), greeting);
        LAST_FLAVOUR.put(player.getUUID(), flavour);
        return Component.literal(greeting).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" " + flavour).withStyle(ChatFormatting.GRAY));
    }

    private static Context context(ServerPlayer player, long previousLastPlayedAtUnixMs) {
        ServerLevel level = player.level();
        boolean monstersNearby = !level.getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(10.0D, 5.0D, 10.0D),
                Monster::isAlive
        ).isEmpty();
        boolean playersNearby = level.players().stream()
                .anyMatch(other -> other != player && other.distanceToSqr(player) <= 32.0D * 32.0D);
        return new Context(
                player,
                level,
                ZonedDateTime.now(MANCHESTER_TIME_ZONE),
                previousLastPlayedAtUnixMs,
                monstersNearby,
                playersNearby
        );
    }

    private static String pick(List<WeightedMessage> messages, Context context, String previous) {
        List<WeightedMessage> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (WeightedMessage message : messages) {
            if (message.condition().test(context) && !message.text().equals(previous)) {
                eligible.add(message);
                totalWeight += message.weight();
            }
        }

        if (eligible.isEmpty()) throw new IllegalStateException("No message condition matched");
        int remaining = ThreadLocalRandom.current().nextInt(totalWeight);
        for (WeightedMessage message : eligible) {
            remaining -= message.weight();
            if (remaining < 0) return message.text();
        }
        throw new IllegalStateException("No weighted message was selected");
    }

    private static WeightedMessage message(int weight, String text, Predicate<Context> condition) {
        return new WeightedMessage(weight, text, condition);
    }

    private record WeightedMessage(int weight, String text, Predicate<Context> condition) {
        private WeightedMessage {
            if (weight <= 0) throw new IllegalArgumentException("Messages need a positive weight");
        }
    }

    private static LocalDate firstWeekday(int year, Month month, DayOfWeek day) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.nextOrSame(day));
    }

    private static LocalDate lastWeekday(int year, Month month, DayOfWeek day) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(day));
    }

    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    private record Context(
            ServerPlayer player,
            ServerLevel level,
            ZonedDateTime now,
            long previousLastPlayedAtUnixMs,
            boolean monstersNearby,
            boolean playersNearby
    ) {
        int hour() {
            return now.getHour();
        }

        DayOfWeek day() {
            return now.getDayOfWeek();
        }

        int year() {
            return now.getYear();
        }

        boolean dateIs(Month month, int day) {
            return dateIs(LocalDate.of(year(), month, day));
        }

        boolean dateIs(LocalDate date) {
            return now.toLocalDate().equals(date);
        }

        boolean withinDaysBefore(LocalDate date, int days) {
            long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), date);
            return daysUntil > 0 && daysUntil <= days;
        }

        LocalDate manchesterPrideWeekendStart() {
            return lastWeekday(year(), Month.AUGUST, DayOfWeek.MONDAY).minusDays(3);
        }

        boolean isManchesterPrideWeekend() {
            LocalDate today = now.toLocalDate();
            LocalDate start = manchesterPrideWeekendStart();
            return !today.isBefore(start) && !today.isAfter(start.plusDays(3));
        }

        boolean isRemembranceSunday() {
            return dateIs(firstWeekday(year(), Month.NOVEMBER, DayOfWeek.SUNDAY).plusWeeks(1));
        }

        boolean isChristmasSeason() {
            return now.getMonth() == Month.DECEMBER && now.getDayOfMonth() < 25;
        }

        boolean isChristmasWeek() {
            return now.getMonth() == Month.DECEMBER
                    && now.getDayOfMonth() >= 18
                    && now.getDayOfMonth() < 25;
        }

        long worldTime() {
            return Math.floorMod(level.getOverworldClockTime(), 24_000L);
        }

        MoonPhase moonPhase() {
            return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, player.blockPosition());
        }

        boolean hasSkyTime() {
            return level.dimension() == Level.OVERWORLD;
        }

        boolean isSunrise() {
            return hasSkyTime() && worldTime() < 1_000L;
        }

        boolean isDay() {
            return hasSkyTime() && worldTime() >= 1_000L && worldTime() < 11_000L;
        }

        boolean isSunset() {
            return hasSkyTime() && worldTime() >= 11_000L && worldTime() < 13_000L;
        }

        boolean isNight() {
            return hasSkyTime() && worldTime() >= 13_000L;
        }

        boolean isSnowing() {
            return level.isRaining()
                    && level.precipitationAt(player.blockPosition()) == Biome.Precipitation.SNOW;
        }

        boolean recentlyPlayed(long maximumAwayTimeMs) {
            return previousLastPlayedAtUnixMs > 0L
                    && now.toInstant().toEpochMilli() - previousLastPlayedAtUnixMs <= maximumAwayTimeMs;
        }

        boolean goneFor(long minimumAwayTimeMs) {
            return previousLastPlayedAtUnixMs > 0L
                    && now.toInstant().toEpochMilli() - previousLastPlayedAtUnixMs >= minimumAwayTimeMs;
        }
    }
}
