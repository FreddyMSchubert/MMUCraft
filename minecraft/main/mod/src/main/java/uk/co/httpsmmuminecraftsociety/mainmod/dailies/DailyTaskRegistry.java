package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonObject;
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
import java.util.Random;
import java.util.Set;

import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class DailyTaskRegistry {
    private static final List<Weighted<List<Weighted<Option>>>> TASKS = DailyTaskCatalog.taskFamilies();

    private DailyTaskRegistry() {
    }

    public static void validate() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Weighted<List<Weighted<Option>>> family : TASKS) {
            if (family.value().isEmpty()) throw new IllegalStateException("Daily task families must not be empty");
            for (Weighted<Option> weighted : family.value()) {
                Option option = weighted.value();
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
    }

    public static List<JsonObject> pick(String seed, int count, Collection<String> excludedIds) {
        List<Weighted<List<Weighted<Option>>>> available = TASKS.stream()
                .map(family -> weighted(family.weight(), family.value().stream()
                        .filter(weighted -> !excludedIds.contains(weighted.value().definition().getId()))
                        .filter(weighted -> weighted.value().available())
                        .toList()))
                .filter(family -> !family.value().isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (count < 1 || count > available.size()) {
            throw new IllegalArgumentException("Invalid daily task count: " + count);
        }

        Random random = new Random(seed.hashCode());
        List<JsonObject> result = new ArrayList<>(count);
        while (result.size() < count) {
            Weighted<List<Weighted<Option>>> family = removeWeighted(random, available);
            result.add(pickWeighted(random, family.value()).create(random));
        }
        return result;
    }

    public static DailyTaskDefinition find(String id) {
        return TASKS.stream()
                .flatMap(family -> family.value().stream())
                .map(Weighted::value)
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

    private static <T> T pickWeighted(Random random, List<Weighted<T>> values) {
        int roll = random.nextInt(values.stream().mapToInt(Weighted::weight).sum());
        for (Weighted<T> value : values) {
            roll -= value.weight();
            if (roll < 0) return value.value();
        }
        throw new IllegalStateException("Weighted daily task selection failed");
    }

    private static <T> Weighted<T> removeWeighted(Random random, List<Weighted<T>> values) {
        int roll = random.nextInt(values.stream().mapToInt(Weighted::weight).sum());
        for (int index = 0; index < values.size(); index++) {
            roll -= values.get(index).weight();
            if (roll < 0) return values.remove(index);
        }
        throw new IllegalStateException("Weighted daily task selection failed");
    }

    static <T> Weighted<T> weighted(int weight, T value) {
        return new Weighted<>(weight, value);
    }

    static boolean hasEnchantment(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), stack
        ) >= level;
    }

    static Weighted<Option> option(
            int weight,
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
        return weighted(weight, new Option(
                nether, end, definition, baseCost, rewardPerIteration,
                minimum, maximum, emoji, name, description
        ));
    }

    record Weighted<T>(int weight, T value) {
		Weighted {
            if (weight < 1) throw new IllegalArgumentException("Daily task weights must be positive");
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
    ) {
		Option {
            if (baseCost < 0 || !Double.isFinite(rewardPerIteration) || rewardPerIteration < 0
                    || minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid daily task reward settings for " + definition.getId());
            }
            if (minimum == maximum && rewardPerIteration != 0) {
                throw new IllegalArgumentException("Fixed daily tasks must put the full reward in baseCost");
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
