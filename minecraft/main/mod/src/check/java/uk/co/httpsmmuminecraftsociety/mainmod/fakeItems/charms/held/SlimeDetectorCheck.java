package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SlimeDetectorCheck {
    private SlimeDetectorCheck() {}

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);

        checkSignalBars();
        checkWorldSeedAffectsResults();
        checkScanDuration();
        checkDefinition(project);
        checkRecipe(project);
        checkKnowledgePage(project);
        checkTextures(project);

        System.out.println("Slime Detector checks passed: signal, seed, timing, item, recipe, knowledge, and textures.");
    }

    private static void checkSignalBars() {
        long[] seeds = {0L, 1L, -1L, 2048005618087379093L};
        for (long seed : seeds) {
            for (int chunkX = -12; chunkX <= 12; chunkX++) {
                for (int chunkZ = -12; chunkZ <= 12; chunkZ++) {
                    ChunkPos origin = new ChunkPos(chunkX, chunkZ);
                    assert SlimeDetectorCharm.signalBars(seed, origin) == expectedSignalBars(seed, origin);
                }
            }
        }
    }

    private static int expectedSignalBars(long seed, ChunkPos origin) {
        int nearest = Integer.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance <= 4 && SlimeDetectorCharm.isSlimeChunk(seed, origin.x() + dx, origin.z() + dz)) {
                    nearest = Math.min(nearest, distance);
                }
            }
        }
        return nearest <= 4 ? 5 - nearest : 0;
    }

    private static void checkWorldSeedAffectsResults() {
        boolean foundDifference = false;
        for (int x = -20; x <= 20 && !foundDifference; x++) {
            for (int z = -20; z <= 20; z++) {
                if (SlimeDetectorCharm.isSlimeChunk(1L, x, z)
                        != SlimeDetectorCharm.isSlimeChunk(2L, x, z)) {
                    foundDifference = true;
                    break;
                }
            }
        }
        assert foundDifference : "The world seed must influence slime chunk results";
    }

    private static void checkScanDuration() {
        LegacyRandomSource random = new LegacyRandomSource(211L);
        boolean sawMinimum = false;
        boolean sawMaximum = false;
        for (int attempt = 0; attempt < 10_000; attempt++) {
            int ticks = SlimeDetectorCharm.randomScanDelayTicks(random);
            assert ticks >= 10 && ticks <= 40;
            sawMinimum |= ticks == 10;
            sawMaximum |= ticks == 40;
        }
        assert sawMinimum && sawMaximum;
    }

    private static void checkDefinition(Path project) throws Exception {
        JsonObject item = readJson(project.resolve("../data/data/items/charm/held/slime-detector/item.json"));
        assert item.get("id").getAsString().equals("charm-slime-detector");
        assert item.get("maxStackSize").getAsInt() == 1;
        JsonObject shop = item.getAsJsonObject("shopPurchasable");
        assert shop.get("priceDabloons").getAsInt() == 100;
        assert shop.get("gameplayToggle").getAsString().equals("welcoming");
        assert item.getAsJsonObject("charm").get("charmId").getAsInt() == SlimeDetectorCharm.CHARM_ID;
    }

    private static void checkRecipe(Path project) throws Exception {
        JsonObject recipe = readJson(project.resolve("src/main/resources/data/mainmod/recipe/slime_detector.json"));
        assert recipe.get("type").getAsString().equals("mainmod:fake_crafting_shapeless");
        assert recipe.get("gameplayToggle").getAsString().equals("welcoming");
        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        assert ingredients.size() == 2;
        assert ingredients.contains(JsonParser.parseString("\"minecraft:compass\""));
        assert ingredients.contains(JsonParser.parseString("\"minecraft:slime_ball\""));
        assert recipe.getAsJsonObject("result").get("stack").getAsString().equals("mainmod:charm-slime-detector");
    }

    private static void checkKnowledgePage(Path project) throws Exception {
        String page = Files.readString(project.resolve("../../../services/web/public/knowledge/01-items/13-slime-detector.md"));
        assert page.contains("id: slime-detector");
        assert page.contains("gameplayToggle: welcoming");
        assert page.contains("Compass");
        assert page.contains("Slime Ball");
        assert page.contains("Manhattan distance");
    }

    private static void checkTextures(Path project) throws Exception {
        Path textures = project.resolve("../respack/packs/general-pack/assets/general-pack/textures/item/slime-detector");
        for (int bars = 0; bars <= 5; bars++) {
            BufferedImage image = ImageIO.read(textures.resolve("slime-detector-" + bars + ".png").toFile());
            assert image != null && image.getWidth() == 16 && image.getHeight() == 16;
        }
        BufferedImage loading = ImageIO.read(textures.resolve("slime-detector-loading.png").toFile());
        assert loading != null && loading.getWidth() == 16;
        assert loading.getHeight() > 16 && loading.getHeight() % 16 == 0;
        assert Files.exists(textures.resolve("slime-detector-loading.png.mcmeta"));
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
