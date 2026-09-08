package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class DailyTaskRegistry {
    public static final int RECENT_TASK_REROLL_LIMIT = 5;
    private static volatile List<Weighted<CatalogDirectory>> tasks = List.of();
    private static volatile Map<String, DailyTaskDefinition> definitions = Map.of();

    private DailyTaskRegistry() {
    }

    public static void load(HolderLookup.Provider registries) {
        var root = FabricLoader.getInstance()
                .getModContainer(MainMod.MOD_ID)
                .orElseThrow()
                .findPath(DailyTaskCatalog.RESOURCE_PATH)
                .orElseThrow(() -> new IllegalStateException("Daily task catalogue is missing"));
        List<Weighted<CatalogDirectory>> loaded = DailyTaskCatalog.load(
                root, registries, FakeItems.ID_MAP.keySet());
        validate(loaded);
        definitions = options(loaded).stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                option -> option.definition().getId(), Option::definition));
        tasks = loaded;
    }

    public static void validate() {
        validate(tasks);
    }

    static void validate(List<Weighted<CatalogDirectory>> tasks) {
        if (tasks.isEmpty()) throw new IllegalStateException("Daily task catalogue must not be empty");
        totalWeight(tasks);
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Option option : options(tasks)) {
            DailyTaskDefinition definition = option.definition();
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
                    || task.get("rewardDabloons").getAsInt() != amount.reward(instances)
                    || task.get("rewardDabloons").getAsInt() != definition.getReward(task)) {
                throw new IllegalStateException("Invalid daily task definition: " + definition.getId());
            }
        }
    }

    public static List<JsonObject> pick(String seed, int count, Collection<String> excludedIds) {
        if (RECENT_TASK_REROLL_LIMIT < 1) throw new IllegalStateException("Daily task reroll limit must be positive");
        List<Weighted<CatalogDirectory>> available = tasks.stream()
                .map(family -> weighted(family.weight(), available(family.value())))
                .filter(family -> family.value() != null)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (count < 1 || count > available.size()) {
            throw new IllegalArgumentException("Invalid daily task count: " + count);
        }

        Random random = new Random(seed.hashCode());
        List<JsonObject> result = new ArrayList<>(count);
        while (result.size() < count) {
            CatalogDirectory family = removeWeighted(random, available).value();
            Option option = null;
            for (int attempt = 0; attempt < RECENT_TASK_REROLL_LIMIT; attempt++) {
                option = pickOption(random, family);
                if (!excludedIds.contains(option.definition().getId())) break;
            }
            result.add(option.create(random));
        }
        return result;
    }

    public static DailyTaskDefinition find(String id) {
        return definitions.get(id);
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

    private static <T> T pickWeighted(Random random, List<Weighted<T>> values) {
        int roll = random.nextInt(totalWeight(values));
        for (Weighted<T> value : values) {
            roll -= value.weight();
            if (roll < 0) return value.value();
        }
        throw new IllegalStateException("Weighted daily task selection failed");
    }

    private static <T> Weighted<T> removeWeighted(Random random, List<Weighted<T>> values) {
        int roll = random.nextInt(totalWeight(values));
        for (int index = 0; index < values.size(); index++) {
            roll -= values.get(index).weight();
            if (roll < 0) return values.remove(index);
        }
        throw new IllegalStateException("Weighted daily task selection failed");
    }

    private static Option pickOption(Random random, CatalogDirectory directory) {
        CatalogEntry entry = pickWeighted(random, directory.entries());
        return entry instanceof Option option ? option : pickOption(random, (CatalogDirectory) entry);
    }

    private static CatalogDirectory available(CatalogDirectory directory) {
        List<Weighted<CatalogEntry>> entries = directory.entries().stream()
                .map(weighted -> {
                    CatalogEntry entry = weighted.value() instanceof Option option
                            ? (option.available() ? option : null)
                            : available((CatalogDirectory) weighted.value());
                    return entry == null ? null : weighted(weighted.weight(), entry);
                })
                .filter(weighted -> weighted != null && weighted.value() != null)
                .toList();
        return entries.isEmpty() ? null : new CatalogDirectory(entries);
    }

    static int optionCount(List<Weighted<CatalogDirectory>> tasks) {
        return options(tasks).size();
    }

    private static List<Option> options(List<Weighted<CatalogDirectory>> tasks) {
        List<Option> result = new ArrayList<>();
        tasks.forEach(family -> collectOptions(family.value(), result));
        return result;
    }

    private static void collectOptions(CatalogDirectory directory, List<Option> result) {
        totalWeight(directory.entries());
        for (Weighted<CatalogEntry> weighted : directory.entries()) {
            if (weighted.value() instanceof Option option) result.add(option);
            else collectOptions((CatalogDirectory) weighted.value(), result);
        }
    }

    private static int totalWeight(List<? extends Weighted<?>> values) {
        int total = 0;
        try {
            for (Weighted<?> value : values) total = Math.addExact(total, value.weight());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Daily task weights are too large");
        }
        return total;
    }

    static <T> Weighted<T> weighted(int weight, T value) {
        return new Weighted<>(weight, value);
    }

    static boolean hasEnchantment(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), stack
        ) >= level;
    }

    static Option option(
            boolean nether,
            boolean end,
            DailyTaskDefinition definition,
            int baseCost,
            double rewardPerIteration,
            int minimum,
            int maximum,
            String emoji,
            String name,
            String description
    ) {
        return new Option(
                nether, end, definition, baseCost, rewardPerIteration,
                minimum, maximum, emoji, name, description
        );
    }

    record Weighted<T>(int weight, T value) {
		Weighted {
            if (weight < 1) throw new IllegalArgumentException("Daily task weights must be positive");
        }
    }

    sealed interface CatalogEntry permits CatalogDirectory, Option {
    }

    record CatalogDirectory(List<Weighted<CatalogEntry>> entries) implements CatalogEntry {
        CatalogDirectory {
            entries = List.copyOf(entries);
            if (entries.isEmpty()) throw new IllegalArgumentException("Daily task directories must not be empty");
        }
    }

    record Option(
            boolean nether,
            boolean end,
            DailyTaskDefinition definition,
            int baseCost,
            double rewardPerIteration,
            int minimum,
            int maximum,
            String emoji,
            String name,
            String description
    ) implements CatalogEntry {
		Option {
            if (baseCost < 0 || !Double.isFinite(rewardPerIteration) || rewardPerIteration < 0
                    || minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid daily task reward settings for " + definition.getId());
            }
            if (new DailyTaskAmount(baseCost, rewardPerIteration).reward(minimum) < 3) {
                throw new IllegalArgumentException("Daily task rewards must start at 3 dabloons for " + definition.getId());
            }
            if (name.isBlank() || description.isBlank() || emoji.isBlank()) {
                throw new IllegalArgumentException("Daily task copy must not be blank");
            }
        }

        private boolean available() {
            return (!nether || FeatureToggles.isEnabled(FeatureToggles.NETHER))
                    && (!end || FeatureToggles.isEnabled(FeatureToggles.END));
        }

        private JsonObject create(Random random) {
            int count = random.nextInt(minimum, maximum + 1);
            JsonObject task = definition.create(count);
            DailyTaskAmount amount = new DailyTaskAmount(baseCost, rewardPerIteration);
            task.addProperty("name", name);
            task.addProperty("description", description.replace("{count}", Integer.toString(count)));
            task.addProperty("emoji", emoji);
            task.addProperty("baseCost", amount.baseCost());
            task.addProperty("rewardPerIteration", amount.perInstance());
            task.addProperty("rewardDabloons", amount.reward(count));
            return task;
        }
    }
}
