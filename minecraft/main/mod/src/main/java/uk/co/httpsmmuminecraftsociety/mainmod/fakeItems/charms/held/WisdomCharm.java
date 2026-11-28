package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WisdomCharm implements UseCallbackCharm {
    private static final String QUOTES_FILE = "data/mainmod/wisdom.json";
    private static final String LAST_QUOTE_TAG = "wisdom_last_quote";
    private static final String LAST_ZEN_QUOTE_TAG = "wisdom_last_zen_quote";
    private static final URI ZEN_API = URI.create("https://zenquotes.io/api/quotes");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    private final List<String> localQuotes;
    private volatile List<String> zenQuotes = List.of();
    private Instant refreshAfter = Instant.EPOCH;
    private boolean refreshing;

    public WisdomCharm() {
        try {
            Path file = FabricLoader.getInstance()
                    .getModContainer(MainMod.MOD_ID)
                    .flatMap(container -> container.findPath(QUOTES_FILE))
                    .orElseThrow(() -> new IllegalStateException("Could not find " + QUOTES_FILE));
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) throw new JsonParseException("Expected a string array in " + file);
                JsonArray array = root.getAsJsonArray();
                List<String> loaded = new ArrayList<>();
                for (JsonElement element : array) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                            || element.getAsString().isBlank()) {
                        throw new JsonParseException("Expected a nonblank string in " + file);
                    }
                    loaded.add(element.getAsString());
                }
                if (loaded.isEmpty()) throw new JsonParseException("No wisdom quotes in " + file);
                localQuotes = List.copyOf(loaded);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load wisdom quotes from " + QUOTES_FILE, e);
        }
        refreshZenQuotes();
    }

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        refreshZenQuotes();
        List<String> availableZenQuotes = zenQuotes;
        boolean zen = !availableZenQuotes.isEmpty() && ThreadLocalRandom.current().nextInt(3) != 0;
        List<String> source = zen ? availableZenQuotes : localQuotes;
        String lastQuoteTag = zen ? LAST_ZEN_QUOTE_TAG : LAST_QUOTE_TAG;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int last = tag.getInt(lastQuoteTag).orElse(-1);
        int index;
        if (source.size() > 1 && last >= 0 && last < source.size()) {
            index = ThreadLocalRandom.current().nextInt(source.size() - 1);
            if (index >= last) index++;
        } else {
            index = ThreadLocalRandom.current().nextInt(source.size());
        }
        tag.putInt(lastQuoteTag, index);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Book of Wisdom"),
                "",
                0,
                List.of(Filterable.passThrough(Component.literal(source.get(index)))),
                true
        ));

        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack realStack = player.getInventory().getSelectedItem().copy();
        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, book));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, realStack));
        return InteractionResult.SUCCESS;
    }

    private synchronized void refreshZenQuotes() {
        if (refreshing || Instant.now().isBefore(refreshAfter)) return;
        refreshing = true;
        HttpRequest request = HttpRequest.newBuilder(ZEN_API)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200 || response.body().length() > 100_000) {
                        throw new IllegalStateException("ZenQuotes returned status " + response.statusCode());
                    }
                    return parseZenQuotes(response.body());
                })
                .whenComplete((loaded, error) -> {
                    synchronized (this) {
                        if (error == null) zenQuotes = loaded;
                        refreshAfter = Instant.now().plus(error == null
                                ? Duration.ofHours(1) : Duration.ofMinutes(1));
                        refreshing = false;
                    }
                    if (error != null) MainMod.LOGGER.warn("Could not refresh Book of Wisdom quotes from ZenQuotes", error);
                });
    }

    static List<String> parseZenQuotes(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) throw new JsonParseException("Expected ZenQuotes array");
        List<String> loaded = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonElement text = element.getAsJsonObject().get("q");
            JsonElement author = element.getAsJsonObject().get("a");
            if (text == null || author == null || !text.isJsonPrimitive() || !author.isJsonPrimitive()
                    || !text.getAsJsonPrimitive().isString() || !author.getAsJsonPrimitive().isString()) continue;
            String quoteText = text.getAsString().strip();
            String authorName = author.getAsString().strip();
            if (!quoteText.isEmpty() && !authorName.isEmpty() && quoteText.length() < 900) {
                loaded.add(quoteText);
            }
        }
        if (loaded.isEmpty()) throw new JsonParseException("ZenQuotes returned no usable quotes");
        return List.copyOf(loaded);
    }
}
