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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.UseCallbackCharm;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class JokeCharm implements Charm, UseCallbackCharm {
    private static final String JOKES_FILE = "data/mainmod/jokes.json";
    private static final String STORED_JOKE_TAG = "joke_charm_joke";

    private static final int MAX_PAGES = 100;
    private static final int MAX_CHARS_PER_PAGE = 900;

    private volatile List<String> strings = List.of();

    public JokeCharm() {
        try {
            reload();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load joke charm jokes from " + JOKES_FILE, e);
        }
    }

    public void reload() throws IOException {
        Path file = jokesFile();

        JsonElement root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        }

        if (root == null || !root.isJsonArray()) {
            throw new JsonParseException("Expected root JSON string array in " + file);
        }

        JsonArray array = root.getAsJsonArray();
        List<String> loaded = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);

            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Expected string at index " + i + " in " + file);
            }

            String value = element.getAsString();
            if (!value.isBlank()) {
                loaded.add(value);
            }
        }

        if (loaded.isEmpty()) {
            throw new JsonParseException("No non-empty strings found in " + file);
        }

        this.strings = List.copyOf(loaded);
    }

    public String randomString() {
        List<String> snapshot = this.strings;

        if (snapshot.isEmpty()) {
            throw new IllegalStateException("JokeCharm has no loaded strings.");
        }

        return snapshot.get(ThreadLocalRandom.current().nextInt(snapshot.size()));
    }

    @Override
    public InteractionResult onUse(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        open(player, "Joke Book", getOrCreateJoke(stack));
        return InteractionResult.SUCCESS;
    }

    public void openRandom(ServerPlayer player) {
        open(player, "Server", randomString());
    }

    public void openRandom(ServerPlayer player, String title) {
        open(player, title, randomString());
    }

    public static void open(ServerPlayer player, String text) {
        open(player, "Server", text);
    }

    public static void open(ServerPlayer player, String title, String text) {
        Objects.requireNonNull(player, "player");

        ItemStack fakeBook = createWrittenBook(
                title,
                player.getGameProfile().name(),
                Objects.toString(text, "")
        );

        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack realClientStack = player.getInventory().getSelectedItem().copy();

        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, fakeBook));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, realClientStack));
    }

    private static ItemStack createWrittenBook(String title, String author, String text) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        List<Filterable<Component>> pages = splitIntoPages(text)
                .stream()
                .map(page -> Filterable.passThrough((Component) Component.literal(page)))
                .toList();

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(clampTitle(title)),
                Objects.toString(author, "Server"),
                0,
                pages,
                true
        );

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static List<String> splitIntoPages(String text) {
        List<String> pages = new ArrayList<>();

        String safeText = Objects.toString(text, "");
        String[] manualPages = safeText.split("\\|\\|", -1);

        for (String manualPage : manualPages) {
            if (pages.size() >= MAX_PAGES) {
                break;
            }

            if (manualPage.isEmpty()) {
                pages.add("");
                continue;
            }

            int index = 0;
            while (index < manualPage.length() && pages.size() < MAX_PAGES) {
                int end = Math.min(index + MAX_CHARS_PER_PAGE, manualPage.length());
                pages.add(manualPage.substring(index, end));
                index = end;
            }
        }

        if (pages.isEmpty()) {
            pages.add("");
        }

        return pages;
    }

    private static String clampTitle(String title) {
        String safeTitle = Objects.toString(title, "Server").strip();

        if (safeTitle.isEmpty()) {
            safeTitle = "Server";
        }

        int maxLength = WrittenBookContent.TITLE_MAX_LENGTH;
        return safeTitle.length() <= maxLength
                ? safeTitle
                : safeTitle.substring(0, maxLength);
    }

    private String getOrCreateJoke(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String storedJoke = tag.getString(STORED_JOKE_TAG)
                .filter(joke -> !joke.isBlank())
                .orElse(null);

        if (storedJoke != null) {
            return storedJoke;
        }

        String joke = randomString();
        tag.putString(STORED_JOKE_TAG, joke);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return joke;
    }

    private static Path jokesFile() {
        return FabricLoader.getInstance()
                .getModContainer(MainMod.MOD_ID)
                .flatMap(container -> container.findPath(JOKES_FILE))
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find " + JOKES_FILE + " in mod " + MainMod.MOD_ID
                ));
    }
}
