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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
            message(4, "Glad youre back!", context -> true),
            message(4, "You're back! Hi!", context -> true),
            message(2, "Hey, Welcome back!", context -> true),
            message(4, "Good to see you!", context -> true),
            message(4, "Happy to have you!", context -> true),
            message(4, "Look who's back!", context -> true),
            message(5, "Good to see you!", context -> true),
            message(3, "Hello there!", context -> true),
            message(1, "Welcome back in the cubicle!", context -> true),

            message(25, "Back already?", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(25, "Miss me?", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Welcome back, that was quick!", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Hey, you again!", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Right back at it.", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "That was fast.", context -> context.recentlyPlayed(15L * MINUTE_MS)),
            message(20, "Picking up where we left off?", context -> context.recentlyPlayed(15L * MINUTE_MS)),

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
            message(30, "Couldn't sleep?", context -> context.hour() < 5),
            message(20, "Still awake?", context -> context.hour() < 5),
            message(18, "Good Morning!", context -> context.hour() >= 5 && context.hour() < 8),
            message(18, "You're up early.", context -> context.hour() >= 5 && context.hour() < 8),
            message(25, "Good Morning, early bird!", context -> context.hour() >= 5 && context.hour() < 8),
            message(15, "Good morning!", context -> context.hour() >= 8 && context.hour() < 12),
            message(15, "Good afternoon!", context -> context.hour() >= 12 && context.hour() < 18),
            message(15, "Good evening!", context -> context.hour() >= 18),

            message(13, "Happy Monday!", context -> context.day() == DayOfWeek.MONDAY),
            message(13, "Happy Tuesday!", context -> context.day() == DayOfWeek.TUESDAY),
            message(13, "Happy Wednesday!", context -> context.day() == DayOfWeek.WEDNESDAY),
            message(13, "Happy Thursday!", context -> context.day() == DayOfWeek.THURSDAY),
            message(13, "Happy Friday!", context -> context.day() == DayOfWeek.FRIDAY),
            message(16, "Happy Surprising Saturday!", context -> context.day() == DayOfWeek.SATURDAY),
            message(16, "Happy Sunday Funday!", context -> context.day() == DayOfWeek.SUNDAY)
    );

    private static final List<WeightedMessage> FLAVOURS = List.of(
			message(50, "", context -> true), // no flavour text - makes it more special if there is one.

            message(10, "Careful, it's stormy out there.", context -> context.level().isThundering()),
            message(10, "Rough weather out there.", context -> context.level().isThundering()),
            message(10, "It's snowing out!", Context::isSnowing),
            message(10, "Snowy out there.", Context::isSnowing),
            message(10, "Stay dry out there.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Don't forget an umbrella.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Rainy one today.", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "It's raning cats and dogs!", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(20, "Come rain or shine, Minecraft time!", context -> context.level().isRaining() && !context.isSnowing() && !context.level().isThundering()),
            message(10, "Beautiful day out.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "What a gorgeous day.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "It's a beautiful day today!", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "Hard to complain about that weather.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(10, "Looking bright today.", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),
            message(5, "The weather is perfect for a picnic!", context -> context.level().dimension() == Level.OVERWORLD && !context.level().isRaining() && !context.isNight()),

            message(5, "Plenty of daylight left.", Context::isDay),
            message(10, "Sunset is close.", Context::isSunset),
            message(6, "Golden hour is coming up.", Context::isSunset),
            message(10, "Catching the sunset?.", Context::isSunset),
            message(14, "Careful out there, it's night.", Context::isNight),

            message(15, "That's quite a view.", context -> context.player().getY() >= 200.0D),
            message(10, "Up in the clouds?", context -> context.player().getY() >= 120.0D),
            message(10, "Thin air up here.", context -> context.player().getY() >= 120.0D),
            message(14, "Deep slate, deeper plans.", context -> context.player().getY() <= -40.0D),
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
            message(6, "Have you signed up for the next few society events?", context -> true),
            message(6, "Have you checked out your dailies?", context -> true)
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
                player.sendSystemMessage(Component.literal("Warning: You have unread message books. Read them ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(WebsiteCommand.takeMeThere("knowledge", "here", ChatFormatting.RED))
                        .append(Component.literal(".").withStyle(ChatFormatting.GOLD)));
            }
            if (!response.getFound()) return;
            player.sendSystemMessage(Component.literal("Tip: ")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal(response.getTip()).withStyle(ChatFormatting.WHITE))
                    .append(" ")
                    .append(WebsiteCommand.takeMeThere(
                            "play/knowledge/" + response.getKnowledgeId(),
                            "[Read more]",
                            ChatFormatting.GOLD
                    )));
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
                player.getBoundingBox().inflate(16.0D),
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
