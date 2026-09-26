package uk.co.httpsmmuminecraftsociety.mainmod.playerpotions;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public final class SkinColors {
    private static final int FALLBACK = 0x9A8B78;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Map<String, CompletableFuture<Integer>> COLORS = new ConcurrentHashMap<>();

    private SkinColors() {}

    public static void prefetch(GameProfile profile) {
        String url = skinUrl(profile);
        if (url != null) COLORS.computeIfAbsent(url, key -> CompletableFuture.supplyAsync(() -> fetch(key)));
    }

    static boolean ready(GameProfile profile) {
        String url = skinUrl(profile);
        if (url == null) return true;
        prefetch(profile);
        return COLORS.get(url).isDone();
    }

    static void color(GameProfile profile, ItemStack potion) {
        String url = skinUrl(profile);
        if (url != null) prefetch(profile);
        int color = url == null ? FALLBACK : COLORS.get(url).getNow(FALLBACK);
        PotionContents old = potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(old.potion(),
                java.util.Optional.of(color), old.customEffects(), old.customName()));
    }

    private static String skinUrl(GameProfile profile) {
        try {
            Property texture = profile.properties().get("textures").stream().findFirst().orElse(null);
            if (texture == null) return null;
            String json = new String(Base64.getDecoder().decode(texture.value()), java.nio.charset.StandardCharsets.UTF_8);
            String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").get("url").getAsString();
            URI uri = URI.create(url);
            if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) return null;
            return uri.getHost().equals("textures.minecraft.net") ? "https://textures.minecraft.net" + uri.getRawPath() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            byte[] bytes = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            if (bytes.length > 1024 * 1024) return FALLBACK;
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > 256 || image.getHeight() > 256) return FALLBACK;
            long red = 0, green = 0, blue = 0, alpha = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int a = pixel >>> 24;
                red += ((pixel >>> 16) & 255) * (long) a;
                green += ((pixel >>> 8) & 255) * (long) a;
                blue += (pixel & 255) * (long) a;
                alpha += a;
            }
            return alpha == 0 ? FALLBACK : ((int) (red / alpha) << 16)
                    | ((int) (green / alpha) << 8) | (int) (blue / alpha);
        } catch (Exception exception) {
            MainMod.LOGGER.warn("Could not load player skin color", exception);
            return FALLBACK;
        }
    }
}
