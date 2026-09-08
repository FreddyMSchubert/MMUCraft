package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskRegistry.Option;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskRegistry.CatalogDirectory;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskRegistry.CatalogEntry;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskRegistry.Weighted;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DailyTaskCatalog {
    static final String RESOURCE_PATH = "data/mainmod/dailies/catalog";
    private static final String WEIGHTS_FILE = "weights.dailyweights.json";
    private static final String TASK_SUFFIX = ".daily.json";
    private static final Set<String> COMMON_FIELDS = Set.of(
            "type", "nether", "end", "baseCost", "rewardPerIteration", "minimum", "maximum",
            "emoji", "name", "description"
    );

    private final HolderLookup.Provider registries;
    private final Set<String> fakeItemIds;

    private DailyTaskCatalog(HolderLookup.Provider registries, Set<String> fakeItemIds) {
        this.registries = registries;
        this.fakeItemIds = Set.copyOf(fakeItemIds);
    }

    static List<Weighted<CatalogDirectory>> load(
            Path root,
            HolderLookup.Provider registries,
            Set<String> fakeItemIds
    ) {
        return new DailyTaskCatalog(registries, fakeItemIds).loadRoot(root);
    }

    private List<Weighted<CatalogDirectory>> loadRoot(Path root) {
        Map<String, Integer> weights = readWeights(root);
        List<Weighted<CatalogDirectory>> families = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            Path family = root.resolve(entry.getKey());
            if (!Files.isDirectory(family)) {
                throw invalid(root, "Root weight '" + entry.getKey() + "' must name a directory");
            }
            families.add(DailyTaskRegistry.weighted(entry.getValue(), loadDirectory(family)));
        }
        if (families.isEmpty()) throw invalid(root, "Daily task catalogue must not be empty");
        return List.copyOf(families);
    }

    private CatalogDirectory loadDirectory(Path directory) {
        List<Weighted<CatalogEntry>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : readWeights(directory).entrySet()) {
            Path child = directory.resolve(entry.getKey());
            if (Files.isDirectory(child)) {
                result.add(DailyTaskRegistry.weighted(entry.getValue(), loadDirectory(child)));
            } else if (Files.isRegularFile(child) && entry.getKey().endsWith(TASK_SUFFIX)) {
                result.add(DailyTaskRegistry.weighted(entry.getValue(), readOption(child)));
            } else {
                throw invalid(child, "A weight must name a directory or *" + TASK_SUFFIX + " file");
            }
        }
        return new CatalogDirectory(result);
    }

    private Map<String, Integer> readWeights(Path directory) {
        if (!Files.isDirectory(directory)) throw invalid(directory, "Daily task catalogue directory does not exist");
        JsonObject json = readObject(directory.resolve(WEIGHTS_FILE));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[a-z0-9][a-z0-9_.-]*") || name.equals(WEIGHTS_FILE)) {
                throw invalid(directory, "Invalid weight key '" + name + "'");
            }
            result.put(name, positiveInteger(entry.getValue(), directory, name));
        }
        Set<String> children = new HashSet<>();
        try (var paths = Files.list(directory)) {
            paths.map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals(WEIGHTS_FILE))
                    .forEach(children::add);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list daily task directory " + directory, exception);
        }
        if (!children.equals(result.keySet())) {
            Set<String> unweighted = new HashSet<>(children);
            unweighted.removeAll(result.keySet());
            Set<String> missing = new HashSet<>(result.keySet());
            missing.removeAll(children);
            throw invalid(directory, "Weights and children differ; unweighted=" + unweighted + ", missing=" + missing);
        }
        if (result.isEmpty()) throw invalid(directory, "Weights must not be empty");
        return result;
    }

    private Option readOption(Path path) {
        JsonObject json = readObject(path);
        String type = string(json, "type", path);
        Set<String> expected = new HashSet<>(COMMON_FIELDS);
        expected.addAll(typeFields(type, path));
        if (!json.keySet().equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(json.keySet());
            Set<String> unknown = new HashSet<>(json.keySet());
            unknown.removeAll(expected);
            throw invalid(path, "Fields differ for type '" + type + "'; missing=" + missing + ", unknown=" + unknown);
        }

        DailyTaskDefinition definition = definition(type, json, path);
        return DailyTaskRegistry.option(
                bool(json, "nether", path),
                bool(json, "end", path),
                definition,
                nonNegativeInteger(json.get("baseCost"), path, "baseCost"),
                nonNegativeDouble(json.get("rewardPerIteration"), path, "rewardPerIteration"),
                positiveInteger(json.get("minimum"), path, "minimum"),
                positiveInteger(json.get("maximum"), path, "maximum"),
                string(json, "emoji", path),
                string(json, "name", path),
                string(json, "description", path)
        );
    }

    private DailyTaskDefinition definition(String type, JsonObject json, Path path) {
        return switch (type) {
            case "submit_item" -> new ItemSubmissionTask(item(json, "item", path));
            case "submit_fake_item" -> ItemSubmissionTask.custom(fakeItem(json, "fakeItem", path));
            case "submit_dyed_item" -> {
                Item item = item(json, "item", path);
                DyeColor color = enumValue(DyeColor.class, string(json, "color", path), path, "color");
                yield ItemSubmissionTask.matching(color.getName(), item, (player, stack) ->
                        stack.get(DataComponents.DYED_COLOR) != null
                                && stack.get(DataComponents.DYED_COLOR).rgb() == color.getTextureDiffuseColor());
            }
            case "submit_enchanted_item" -> {
                Item item = item(json, "item", path);
                ResourceKey<Enchantment> enchantment = registryKey(Registries.ENCHANTMENT, json, "enchantment", path);
                registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
                int level = positiveInteger(json.get("minimumEnchantmentLevel"), path, "minimumEnchantmentLevel");
                String suffix = enchantment.identifier().getPath().replace('_', '-') + (level == 1 ? "" : "-" + level);
                yield ItemSubmissionTask.matching(suffix, item,
                        (player, stack) -> DailyTaskRegistry.hasEnchantment(player, stack, enchantment, level));
            }
            case "submit_remaining_durability_item" -> {
                Item item = item(json, "item", path);
                int remaining = positiveInteger(json.get("remainingDurability"), path, "remainingDurability");
                yield ItemSubmissionTask.matching(remaining == 1 ? "one-durability" : remaining + "-durability", item,
                        (player, stack) -> stack.getMaxDamage() - stack.getDamageValue() == remaining);
            }
            case "submit_below_half_durability_item" -> ItemSubmissionTask.matching(
                    "under-half-durability", item(json, "item", path),
                    (player, stack) -> stack.getDamageValue() * 2 > stack.getMaxDamage());
            case "submit_bee_nest_with_bees" -> ItemSubmissionTask.matching(
                    "with-bees", item(json, "item", path),
                    (player, stack) -> stack.has(DataComponents.BLOCK_ENTITY_DATA)
                            && !stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId().getListOrEmpty("Bees").isEmpty());
            case "submit_ominous_banner" -> ItemSubmissionTask.matching(
                    "ominous", item(json, "item", path),
                    (player, stack) -> ItemStack.isSameItemSameComponents(
                            stack,
                            Raid.getOminousBannerInstance(player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))));
            case "submit_potion" -> {
                Holder<Potion> potion = holder(Registries.POTION, json, "potion", path);
                yield ItemSubmissionTask.matching(
                        potion.unwrapKey().orElseThrow().identifier().getPath().replace('_', '-'),
                        item(json, "item", path),
                        (player, stack) -> stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion));
            }
            case "break_block" -> new BreakBlockTask(block(json, "block", path));
            case "breed_entity" -> new BreedEntityTask(entity(json, "entity", path));
            case "brew_potion" -> new BrewPotionTask(holder(Registries.POTION, json, "potion", path));
            case "brush_block" -> new BrushBlockTask(block(json, "block", path));
            case "craft_item" -> new CraftItemTask(item(json, "item", path));
            case "create_golem" -> new CreateGolemTask(entity(json, "golem", path));
            case "cure_zombie_villager" -> new CureZombieVillagerTask();
            case "eat_item" -> new EatItemTask(item(json, "item", path));
            case "enchant_at_table" -> new EnchantAtTableTask();
            case "enchant_item" -> enchantItem(itemOrTag(json, "itemType", path));
            case "feed_entity" -> new FeedEntityTask(entity(json, "entity", path));
            case "fish_anything" -> new FishTask();
            case "fish_item" -> new FishTask(item(json, "item", path));
            case "fish_fake_item" -> FishTask.custom(fakeItem(json, "fakeItem", path));
            case "gain_levels" -> new GainLevelsTask();
            case "hit_player_with_projectile" -> new HitPlayerWithProjectileTask(entity(json, "projectile", path));
            case "kill_entity" -> new KillEntityTask(entity(json, "entity", path));
            case "kill_with_item" -> killWithItem(itemOrTag(json, "item", path));
            case "plant_crop" -> new PlantCropTask(item(json, "seed", path));
            case "play_note_block" -> new PlayNoteBlockTask(enumValue(
                    NoteBlockInstrument.class, string(json, "instrument", path), path, "instrument"));
            case "play_time" -> new PlayTimeTask();
            case "receive_effect" -> new ReceiveEffectTask(holder(Registries.MOB_EFFECT, json, "effect", path));
            case "ride_distance" -> new RideDistanceTask(entity(json, "vehicle", path));
            case "simple_event" -> new SimpleEventTask(
                    enumValue(DailySimpleEvent.class, string(json, "event", path), path, "event"),
                    string(json, "progressLabel", path),
                    string(json, "progressUnit", path));
            case "take_damage_type" -> {
                ResourceKey<DamageType> damageType = registryKey(Registries.DAMAGE_TYPE, json, "damageType", path);
                registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(damageType);
                yield new TakeDamageTask(damageType);
            }
            case "take_damage_entity" -> new TakeDamageTask(entity(json, "entity", path));
            case "use_charm" -> new UseCharmTask(namespacedFakeItem(json, "charm", path));
            case "use_item" -> new UseItemTask(item(json, "item", path));
            case "villager_trade" -> new VillagerTradeTask(enumValue(
                    VillagerTradeTask.Mode.class, string(json, "tradeMode", path), path, "tradeMode"));
            case "villager_trade_item" -> villagerTradeItem(json, path);
            case "villager_trade_profession" -> {
                ResourceKey<VillagerProfession> profession = registryKey(
                        Registries.VILLAGER_PROFESSION, json, "profession", path);
                registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(profession);
                yield new VillagerTradeTask(profession);
            }
            default -> throw invalid(path, "Unknown daily task type '" + type + "'");
        };
    }

    private static DailyTaskDefinition enchantItem(ItemTarget target) {
        return target.tag() == null ? new EnchantItemTask(target.item()) : new EnchantItemTask(target.tag());
    }

    private static DailyTaskDefinition killWithItem(ItemTarget target) {
        return target.tag() == null ? new KillWithItemTask(target.item()) : new KillWithItemTask(target.tag());
    }

    private DailyTaskDefinition villagerTradeItem(JsonObject json, Path path) {
        VillagerTradeTask.Mode mode = enumValue(
                VillagerTradeTask.Mode.class, string(json, "tradeMode", path), path, "tradeMode");
        Item item = item(json, "target", path);
        if (mode == VillagerTradeTask.Mode.GIVE_ITEM) return VillagerTradeTask.give(item);
        if (mode == VillagerTradeTask.Mode.RECEIVE_ITEM) return new VillagerTradeTask(item);
        throw invalid(path, "villager_trade_item requires give_item or receive_item mode");
    }

    private static Set<String> typeFields(String type, Path path) {
        return switch (type) {
            case "submit_item", "eat_item", "craft_item", "use_item", "fish_item" -> Set.of("item");
            case "submit_fake_item", "fish_fake_item" -> Set.of("fakeItem");
            case "submit_dyed_item" -> Set.of("item", "color");
            case "submit_enchanted_item" -> Set.of("item", "enchantment", "minimumEnchantmentLevel");
            case "submit_remaining_durability_item" -> Set.of("item", "remainingDurability");
            case "submit_below_half_durability_item", "submit_bee_nest_with_bees", "submit_ominous_banner" -> Set.of("item");
            case "submit_potion" -> Set.of("item", "potion");
            case "break_block", "brush_block" -> Set.of("block");
            case "breed_entity", "feed_entity", "kill_entity", "take_damage_entity" -> Set.of("entity");
            case "brew_potion" -> Set.of("potion");
            case "create_golem" -> Set.of("golem");
            case "enchant_item" -> Set.of("itemType");
            case "hit_player_with_projectile" -> Set.of("projectile");
            case "kill_with_item" -> Set.of("item");
            case "plant_crop" -> Set.of("seed");
            case "play_note_block" -> Set.of("instrument");
            case "receive_effect" -> Set.of("effect");
            case "ride_distance" -> Set.of("vehicle");
            case "simple_event" -> Set.of("event", "progressLabel", "progressUnit");
            case "take_damage_type" -> Set.of("damageType");
            case "use_charm" -> Set.of("charm");
            case "villager_trade" -> Set.of("tradeMode");
            case "villager_trade_item" -> Set.of("tradeMode", "target");
            case "villager_trade_profession" -> Set.of("tradeMode", "profession");
            case "cure_zombie_villager", "enchant_at_table", "fish_anything", "gain_levels", "play_time" -> Set.of();
            default -> throw invalid(path, "Unknown daily task type '" + type + "'");
        };
    }

    private Item item(JsonObject json, String field, Path path) {
        Identifier id = identifier(string(json, field, path), path, field);
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> invalid(path, "Unknown Minecraft item id '" + id + "' in '" + field + "'"));
    }

    private Block block(JsonObject json, String field, Path path) {
        Identifier id = identifier(string(json, field, path), path, field);
        return BuiltInRegistries.BLOCK.getOptional(id)
                .orElseThrow(() -> invalid(path, "Unknown Minecraft block id '" + id + "' in '" + field + "'"));
    }

    private EntityType<?> entity(JsonObject json, String field, Path path) {
        Identifier id = identifier(string(json, field, path), path, field);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .orElseThrow(() -> invalid(path, "Unknown Minecraft entity id '" + id + "' in '" + field + "'"));
    }

    private ItemTarget itemOrTag(JsonObject json, String field, Path path) {
        String value = string(json, field, path);
        if (!value.startsWith("#")) return new ItemTarget(item(json, field, path), null);
        TagKey<Item> tag = TagKey.create(Registries.ITEM, identifier(value.substring(1), path, field));
        registries.lookupOrThrow(Registries.ITEM).get(tag)
                .orElseThrow(() -> invalid(path, "Unknown Minecraft item tag '" + value + "' in '" + field + "'"));
        return new ItemTarget(null, tag);
    }

    private <T> Holder<T> holder(
            ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
            JsonObject json,
            String field,
            Path path
    ) {
        return registries.lookupOrThrow(registry).getOrThrow(registryKey(registry, json, field, path));
    }

    private static <T> ResourceKey<T> registryKey(
            ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
            JsonObject json,
            String field,
            Path path
    ) {
        return ResourceKey.create(registry, identifier(string(json, field, path), path, field));
    }

    private String fakeItem(JsonObject json, String field, Path path) {
        String id = string(json, field, path);
        if (!fakeItemIds.contains(id)) throw invalid(path, "Unknown fake item id '" + id + "' in '" + field + "'");
        return id;
    }

    private String namespacedFakeItem(JsonObject json, String field, Path path) {
        Identifier id = identifier(string(json, field, path), path, field);
        if (!id.getNamespace().equals("mainmod") || !fakeItemIds.contains(id.getPath())) {
            throw invalid(path, "Unknown fake item id '" + id + "' in '" + field + "'");
        }
        return id.toString();
    }

    private static JsonObject readObject(Path path) {
        try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(input)) {
            JsonObject result = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (result.has(name)) throw invalid(path, "Duplicate JSON field '" + name + "'");
                result.add(name, JsonParser.parseReader(reader));
            }
            reader.endObject();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw invalid(path, "JSON must contain exactly one object");
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException illegalState) throw illegalState;
            throw new IllegalStateException("Could not read daily task JSON " + path, exception);
        }
    }

    private static boolean bool(JsonObject json, String field, Path path) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalid(path, "'" + field + "' must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String string(JsonObject json, String field, Path path) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw invalid(path, "'" + field + "' must be a non-blank string");
        }
        return value.getAsString();
    }

    private static int positiveInteger(JsonElement value, Path path, String field) {
        int result = integer(value, path, field);
        if (result < 1) throw invalid(path, "'" + field + "' must be a positive integer");
        return result;
    }

    private static int nonNegativeInteger(JsonElement value, Path path, String field) {
        int result = integer(value, path, field);
        if (result < 0) throw invalid(path, "'" + field + "' must be a non-negative integer");
        return result;
    }

    private static int integer(JsonElement value, Path path, String field) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(path, "'" + field + "' must be an integer");
        }
    }

    private static double nonNegativeDouble(JsonElement value, Path path, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(path, "'" + field + "' must be a number");
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < 0) throw invalid(path, "'" + field + "' must be a finite non-negative number");
        return result;
    }

    private static Identifier identifier(String value, Path path, String field) {
        if (!value.contains(":")) throw invalid(path, "'" + field + "' must be a namespaced id");
        try {
            return Identifier.parse(value);
        } catch (RuntimeException exception) {
            throw invalid(path, "'" + field + "' is not a valid id: '" + value + "'");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, Path path, String field) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(path, "Unknown " + field + " id '" + value + "'");
        }
    }

    private static IllegalStateException invalid(Path path, String message) {
        return new IllegalStateException(path + ": " + message);
    }

    private record ItemTarget(Item item, TagKey<Item> tag) {
    }
}
