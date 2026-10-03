package uk.co.mmuminecraftsociety.waitingroom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WaitingRoomPlugin extends JavaPlugin implements Listener {
    private static final List<NamedTextColor> TEAM_COLORS = List.of(
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW,
            NamedTextColor.WHITE);
    private static final int SPAWN_X = 8;
    private static final int SPAWN_Y = -51;
    private static final int SPAWN_Z = 2;
    private static final Material[] MUSIC_DISCS = java.util.Arrays.stream(Material.values())
            .filter(material -> material.name().startsWith("MUSIC_DISC_")).toArray(Material[]::new);
    private static final String MODE_CHANNEL = "mmucraft:waiting_mode";
    private static final String API_URL = System.getenv().getOrDefault("WAITING_ROOM_API_URL", "http://api:8080");
    private static final String SECRET = System.getenv().getOrDefault("WAITING_ROOM_API_SECRET", "");
    private static final URI JOKE_URL = URI.create("https://icanhazdadjoke.com/");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<UUID, JsonObject> profiles = new ConcurrentHashMap<>();
    private final JokeHistory jokes = new JokeHistory();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean pollingCommands = new AtomicBoolean();
    private long chatCursor = -1;
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new ChunkGenerator() {
            @Override public boolean shouldGenerateNoise() { return false; }
            @Override public boolean shouldGenerateSurface() { return false; }
            @Override public boolean shouldGenerateCaves() { return false; }
            @Override public boolean shouldGenerateDecorations() { return false; }
            @Override public boolean shouldGenerateMobs() { return false; }
            @Override public boolean shouldGenerateStructures() { return false; }
        };
    }

    @Override
    public void onEnable() {
        if (SECRET.isBlank()) {
            getLogger().severe("WAITING_ROOM_API_SECRET is missing");
            Bukkit.shutdown();
            return;
        }
        Bukkit.setWhitelist(false);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, MODE_CHANNEL);
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::refreshProfile),
                1, 20 * 60);
        Bukkit.getScheduler().runTaskTimer(this, this::pollDiscord, 1, 40);
        Bukkit.getScheduler().runTaskTimer(this, this::pollCommands, 20, 40);
    }

    @EventHandler
    public void worldLoaded(WorldLoadEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        world.setSpawnLocation(spawnLocation(world));
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(7.5, 7.5);
        border.setSize(96);
        border.setDamageAmount(0);
        try {
            Files.writeString(Path.of("/data/waiting-room.ready"), "ready\n");
        } catch (IOException error) {
            getLogger().severe("Could not mark the waiting room ready: " + error);
            Bukkit.shutdown();
        }
    }

    @EventHandler
    public void joined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        giveLoadout(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(false);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.teleport(spawnLocation(Bukkit.getWorlds().getFirst()));
        Bukkit.getScheduler().runTask(this, () -> sendMode(player));
        refreshProfile(player);
    }

    @EventHandler
    public void died(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setShouldDropExperience(false);
    }

    @EventHandler
    public void respawned(PlayerRespawnEvent event) {
        event.setRespawnLocation(spawnLocation(Bukkit.getWorlds().getFirst()));
        Bukkit.getScheduler().runTask(this, () -> giveLoadout(event.getPlayer()));
    }

    private static Location spawnLocation(World world) {
        return new Location(world, SPAWN_X, SPAWN_Y, SPAWN_Z);
    }

    private static void giveLoadout(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        player.getEnderChest().clear();
        inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inventory.setItem(1, new ItemStack(Material.SNOWBALL, 64));
        inventory.setItem(2, maxEnchanted(Material.DIAMOND_SPEAR));
        inventory.setItem(3, maxEnchanted(Material.MACE));
        inventory.setItem(4, maxEnchanted(Material.FISHING_ROD));
        inventory.setItem(5, maxEnchanted(Material.TRIDENT));
        inventory.setItem(8, new ItemStack(MUSIC_DISCS[ThreadLocalRandom.current().nextInt(MUSIC_DISCS.length)]));
        for (int slot = 9; slot < 18; slot++) inventory.setItem(slot, new ItemStack(Material.SNOWBALL, 64));
    }

    private static ItemStack maxEnchanted(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.isCursed() || !enchantment.canEnchantItem(item)
                    || meta.getEnchants().keySet().stream().anyMatch(enchantment::conflictsWith)) continue;
            meta.addEnchant(enchantment, enchantment.getMaxLevel(), true);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameModeChanged(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(this, () -> sendMode(event.getPlayer()));
    }

    private void sendMode(Player player) {
        if (!player.isOnline()) return;
        boolean visiting = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        player.sendPluginMessage(this, MODE_CHANNEL, new byte[] { (byte) (visiting ? 1 : 0) });
    }

    @EventHandler public void left(PlayerQuitEvent event) {
        event.quitMessage(null);
        profiles.remove(event.getPlayer().getUniqueId());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(teamName(event.getPlayer()));
        if (team != null) team.unregister();
        board.resetScores(event.getPlayer().getName());
    }
    @EventHandler public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        event.setCancelled(true);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(50) != 0) return;

        World world = player.getWorld();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = random.nextInt(-14, 30);
            int z = random.nextInt(-12, 12);
            if (!world.getBlockAt(x, -61, z).getType().isSolid()
                    || !world.getBlockAt(x, -60, z).isPassable()
                    || !world.getBlockAt(x, -59, z).isPassable()) continue;
            Location destination = player.getLocation();
            destination.setX(x + 0.5);
            destination.setY(-60);
            destination.setZ(z + 0.5);
            if (player.teleport(destination)) return;
        }
    }
    @EventHandler public void hunger(FoodLevelChangeEvent event) { event.setCancelled(true); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("joke")) return false;
        if (args.length == 0 || String.join(" ", args).isBlank()) {
            sender.sendMessage(Component.text("Usage: /joke <station id>", NamedTextColor.RED));
            return true;
        }
        Player player;
        if (sender instanceof BlockCommandSender block) {
            Location station = block.getBlock().getLocation().add(0.5, 0.5, 0.5);
            player = block.getBlock().getWorld().getNearbyPlayers(station, 5).stream()
                    .min(Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(station)))
                    .orElse(null);
        } else if (sender instanceof Player caller) {
            player = caller;
        } else {
            player = null;
        }
        if (player == null || !player.isOnline()) {
            sender.sendMessage(Component.text("No player is near this joke station.", NamedTextColor.RED));
            return true;
        }
        String station = String.join(" ", args).trim();
        if (!jokes.tryStart(station)) {
            player.sendMessage(Component.text(
                    "all outta jokes rn, find another joke station. this one needs time to refuel.",
                    NamedTextColor.YELLOW));
            return true;
        }
        HttpRequest request = HttpRequest.newBuilder(JOKE_URL)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "text/plain")
                .header("User-Agent", "MMUCraft-Waiting-Room/1.0")
                .GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    String joke = response != null && response.statusCode() == 200 ? response.body().strip() : "";
                    boolean success = error == null && !joke.isBlank();
                    jokes.finish(station, success);
                    if (!player.isOnline()) return;
                    if (success) player.sendMessage(Component.text(joke, NamedTextColor.GOLD));
                    else player.sendMessage(Component.text("The joke machine is down. Try again later.", NamedTextColor.RED));
                }));
        return true;
    }

    @EventHandler
    public void chat(AsyncChatEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        String content = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (content.isEmpty() || content.length() > 500) return;
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUniqueId().toString());
        body.addProperty("content", content);
        send("/api/internal/velocity/waiting-chat", body).thenAccept(response -> {
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                getLogger().warning("Waiting chat API returned " + response.statusCode());
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline()) player.sendMessage(Component.text(
                            "Your message did not reach Discord.", NamedTextColor.RED));
                });
            }
        }).exceptionally(error -> {
            getLogger().warning("Waiting chat failed: " + error);
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.sendMessage(Component.text(
                        "Your message did not reach Discord.", NamedTextColor.RED));
            });
            return null;
        });
        Bukkit.getScheduler().runTask(this, () -> {
            JsonObject profile = profiles.get(player.getUniqueId());
            String role = profile == null ? "Player" : profile.get("role").getAsString();
            TextColor color = profile == null ? NamedTextColor.WHITE
                    : TextColor.color(displayColor(profile.get("color").getAsString()));
            Component line = Component.text(player.getName(), color)
                    .append(role.equals("Player") ? Component.empty()
                            : Component.text(" [" + role + "]", NamedTextColor.GRAY))
                    .append(Component.text(": " + content, NamedTextColor.WHITE));
            Bukkit.getOnlinePlayers().forEach(viewer -> viewer.sendMessage(line));
        });
    }

    private void refreshProfile(Player player) {
        send("/api/internal/surprising-saturday/player-data/" + player.getUniqueId(), null)
                .thenAccept(response -> {
                    if (response.statusCode() != 200) return;
                    JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (player.isOnline()) applyProfile(player, profile);
                    });
                }).exceptionally(error -> { getLogger().warning("Waiting profile failed: " + error); return null; });
    }

    private void applyProfile(Player player, JsonObject profile) {
        profiles.put(player.getUniqueId(), profile);
        int color = displayColor(profile.get("color").getAsString());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.addEntry(player.getName());
        team.color(teamColor(color));
        String role = profile.get("role").getAsString();
        String label = switch (role) {
            case "Committee" -> " [Committee]";
            case "External" -> " [External]";
            case "Member" -> " [Member]";
            default -> "";
        };
        NamedTextColor labelColor = switch (role) {
            case "Committee" -> NamedTextColor.AQUA;
            case "External" -> NamedTextColor.GRAY;
            default -> NamedTextColor.GREEN;
        };
        team.suffix(Component.text(label, labelColor));
        Objective objective = board.getObjective("waiting_profile");
        if (objective == null) objective = board.registerNewObjective("waiting_profile", Criteria.DUMMY,
                Component.empty());
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        objective.getScore(player.getName()).numberFormat(
                NumberFormat.fixed(Component.text(profile.get("belowName").getAsString())));
        player.playerListName(Component.text(player.getName(), TextColor.color(color))
                .append(Component.text(label, labelColor)));
    }

    private static String teamName(Player player) {
        return "wr" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
    }

    private void pollDiscord() {
        if (!polling.compareAndSet(false, true)) return;
        String path = "/api/internal/velocity/waiting-chat" + (chatCursor < 0 ? "" : "?after=" + chatCursor);
        send(path, null).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            chatCursor = result.get("cursor").getAsLong();
            Bukkit.getScheduler().runTask(this, () -> result.getAsJsonArray("messages").forEach(entry -> {
                JsonObject message = entry.getAsJsonObject();
                Component line = Component.text("[Discord] " + message.get("name").getAsString() + ": "
                        + message.get("content").getAsString(), NamedTextColor.AQUA);
                Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(line));
            }));
        }).exceptionally(error -> { getLogger().warning("Discord poll failed: " + error); return null; })
                .whenComplete((ignored, error) -> polling.set(false));
    }

    private static NamedTextColor teamColor(int color) {
        Oklab source = oklab(color);
        NamedTextColor closest = NamedTextColor.WHITE;
        double shortest = Double.POSITIVE_INFINITY;
        for (NamedTextColor candidate : TEAM_COLORS) {
            Oklab target = oklab(candidate.value());
            double lightness = source.lightness - target.lightness;
            double a = source.a - target.a;
            double b = source.b - target.b;
            double chroma = source.chroma() - target.chroma();
            double hueDistance = Math.max(0, a * a + b * b - chroma * chroma);
            double distance = lightness * lightness + 0.5 * chroma * chroma + 2 * hueDistance;
            if (target.chroma() < 0.0001)
                distance += Math.min(0.025, Math.max(0, source.chroma() - 0.02) * 0.4);
            if (distance < shortest) {
                closest = candidate;
                shortest = distance;
            }
        }
        return closest;
    }

    private static int displayColor(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int red = rgb >> 16 & 0xff;
        int green = rgb >> 8 & 0xff;
        int blue = rgb & 0xff;
        double lightness = (Math.max(red, Math.max(green, blue)) + Math.min(red, Math.min(green, blue))) / 510.0;
        if (lightness >= 0.6) return rgb;
        double whiteBlend = (0.6 - lightness) / (1 - lightness);
        red = (int) Math.round(red + (255 - red) * whiteBlend);
        green = (int) Math.round(green + (255 - green) * whiteBlend);
        blue = (int) Math.round(blue + (255 - blue) * whiteBlend);
        return red << 16 | green << 8 | blue;
    }

    private static Oklab oklab(int rgb) {
        double red = linearChannel(rgb >> 16 & 0xff);
        double green = linearChannel(rgb >> 8 & 0xff);
        double blue = linearChannel(rgb & 0xff);
        double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new Oklab(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private record Oklab(double lightness, double a, double b) {
        double chroma() { return Math.hypot(a, b); }
    }

    private void pollCommands() {
        if (!pollingCommands.compareAndSet(false, true)) return;
        send("/api/internal/velocity/waiting-commands", null).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            Bukkit.getScheduler().runTask(this, () -> result.getAsJsonArray("commands").forEach(entry -> {
                JsonObject request = entry.getAsJsonObject();
                int id = request.get("id").getAsInt();
                String command = request.get("command").getAsString().replaceFirst("^/", "");
                StringBuilder output = new StringBuilder();
                var sender = Bukkit.createCommandSender(message -> {
                    if (!output.isEmpty()) output.append('\n');
                    output.append(PlainTextComponentSerializer.plainText().serialize(message));
                });
                try {
                    boolean found = Bukkit.dispatchCommand(sender, command);
                    if (!found && output.isEmpty()) output.append("Unknown command.");
                } catch (Exception error) {
                    getLogger().warning("Waiting room console command failed: " + error);
                    output.append("Command failed: ").append(error.getMessage());
                }
                JsonObject body = new JsonObject();
                body.addProperty("output", output.isEmpty() ? "Command completed." : output.substring(0, Math.min(output.length(), 8000)));
                send("/api/internal/velocity/waiting-commands/" + id + "/result", body)
                        .exceptionally(error -> { getLogger().warning("Command result failed: " + error); return null; });
            }));
        }).exceptionally(error -> { getLogger().warning("Command poll failed: " + error); return null; })
                .whenComplete((ignored, error) -> pollingCommands.set(false));
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> send(String path, JsonObject body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API_URL + path))
                .timeout(Duration.ofSeconds(5))
                .header("authorization", "Bearer " + SECRET);
        if (body == null) request.GET();
        else request.header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
