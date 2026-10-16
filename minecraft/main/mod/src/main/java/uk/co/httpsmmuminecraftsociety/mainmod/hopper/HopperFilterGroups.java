package uk.co.httpsmmuminecraftsociety.mainmod.hopper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class HopperFilterGroups {
    private static final Set<String> POTION_ITEMS = Set.of(
            "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion");

    public record Group(
            String id,
            String name,
            Set<String> items,
            Set<String> potions,
            Set<String> potionItems,
            Set<String> enchantments,
            Set<String> enchantmentNamespaces
    ) {
        boolean matches(ItemStack stack) {
            if (items.contains(HopperFilter.baseItemId(stack))) return true;
            if ((potionItems.isEmpty() || potionItems.contains(HopperFilter.baseItemId(stack)))
                    && HopperFilter.potionId(stack).filter(potions::contains).isPresent()) return true;
            Set<String> stored = HopperFilter.storedEnchantmentIds(stack);
            if (stored.stream().anyMatch(enchantments::contains)) return true;
            return !stored.isEmpty() && !enchantmentNamespaces.isEmpty()
                    && stored.stream().allMatch(id -> enchantmentNamespaces.contains(Identifier.parse(id).getNamespace()));
        }

        int matchBreadth(int potionTypeCount, Map<String, Integer> enchantmentCounts) {
            return items.stream().mapToInt(item -> POTION_ITEMS.contains(item) ? potionTypeCount
                    : item.equals("minecraft:enchanted_book")
                    ? enchantmentCounts.values().stream().mapToInt(Integer::intValue).sum() : 1).sum()
                    + potions.size() * (potionItems.isEmpty() ? POTION_ITEMS.size() : potionItems.size())
                    + enchantments.size()
                    + enchantmentNamespaces.stream().mapToInt(namespace -> enchantmentCounts.getOrDefault(namespace, 2)).sum();
        }
    }

    private static volatile Map<String, Group> groups = Map.of();

    private HopperFilterGroups() {}

    public static void load(ResourceManager manager) {
        Map<Identifier, Resource> resources = manager.listResources(
                "dont_edit_auto_generated/hopper_filter_groups",
                id -> id.getNamespace().equals(MainMod.MOD_ID) && id.getPath().endsWith(".json")
        );
        Map<String, Group> loaded = new LinkedHashMap<>();

        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    String path = entry.getKey().getPath();
                    String id = path.substring("dont_edit_auto_generated/hopper_filter_groups/".length(), path.length() - ".json".length());
                    try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        String name = json.get("name").getAsString();
                        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");

                        Set<String> items = readIds(json, "items", HopperFilterGroups::validateItemId);
                        Set<String> potions = readIds(json, "potions", HopperFilterGroups::validateMinecraftId);
                        Set<String> potionItems = readIds(json, "potion_items", HopperFilterGroups::validatePotionItemId);
                        Set<String> enchantments = readIds(json, "enchantments", HopperFilterGroups::validateEnchantmentId);
                        Set<String> enchantmentNamespaces = readIds(json, "enchantment_namespaces",
                                HopperFilterGroups::validateEnchantmentNamespace);
                        if (!potionItems.isEmpty() && potions.isEmpty()) {
                            throw new IllegalArgumentException("potion_items requires potions");
                        }
                        if (items.isEmpty() && potions.isEmpty() && enchantments.isEmpty()
                                && enchantmentNamespaces.isEmpty()) {
                            throw new IllegalArgumentException("a group must contain items, potions, or enchantments");
                        }
                        if (loaded.put(id, new Group(id, name, items, potions, potionItems, enchantments,
                                enchantmentNamespaces)) != null) {
                            throw new IllegalArgumentException("duplicate group id: " + id);
                        }
                    } catch (Exception exception) {
                        throw new IllegalStateException(entry.getKey() + ": " + exception.getMessage(), exception);
                    }
                });

        groups = Map.copyOf(loaded);
        MainMod.LOGGER.info("Loaded {} hopper filter groups: {}", groups.size(), groups.keySet());
    }

    public static List<Group> containingAll(Collection<ItemStack> stacks) {
        int potionTypeCount = (int) groups.values().stream()
                .flatMap(group -> group.potions().stream()).distinct().count();
        Map<String, Set<String>> knownEnchantments = new HashMap<>();
        for (Group group : groups.values()) {
            for (String id : group.enchantments()) {
                knownEnchantments.computeIfAbsent(Identifier.parse(id).getNamespace(), ignored -> new HashSet<>()).add(id);
            }
            for (String namespace : group.enchantmentNamespaces()) {
                knownEnchantments.computeIfAbsent(namespace, ignored -> new HashSet<>());
            }
        }
        Map<String, Integer> enchantmentCounts = new HashMap<>();
        knownEnchantments.forEach((namespace, ids) -> enchantmentCounts.put(namespace, Math.max(2, ids.size())));
        return groups.values().stream()
                .filter(group -> stacks.stream().allMatch(group::matches))
                .sorted(Comparator.comparingInt((Group group) -> group.matchBreadth(potionTypeCount, enchantmentCounts))
                        .thenComparing(Group::id))
                .toList();
    }

    public static boolean contains(String groupId, ItemStack stack) {
        Group group = groups.get(groupId);
        return group != null && group.matches(stack);
    }

    public static String name(String groupId) {
        Group group = groups.get(groupId);
        return group == null ? groupId : group.name();
    }

    private static void validateItemId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("invalid item id: " + raw);
        if (id.getNamespace().equals("minecraft")) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("unknown item: " + raw);
            return;
        }
        if (id.getNamespace().equals(MainMod.MOD_ID) && FakeItems.isKnownFakeItem(id.getPath())) return;
        throw new IllegalArgumentException("use minecraft: for vanilla items or mainmod: for fake items: " + raw);
    }

    private static void validateMinecraftId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null || !id.getNamespace().equals("minecraft")) {
            throw new IllegalArgumentException("expected a minecraft: ID: " + raw);
        }
    }

    private static void validateEnchantmentId(String raw) {
        if (Identifier.tryParse(raw) == null) {
            throw new IllegalArgumentException("invalid enchantment ID: " + raw);
        }
    }

    private static void validateEnchantmentNamespace(String raw) {
        Identifier id = Identifier.tryParse(raw + ":example");
        if (id == null || !id.getNamespace().equals(raw)) {
            throw new IllegalArgumentException("invalid enchantment namespace: " + raw);
        }
    }

    private static void validatePotionItemId(String raw) {
        if (!POTION_ITEMS.contains(raw)) throw new IllegalArgumentException("not a potion item: " + raw);
    }

    private static Set<String> readIds(JsonObject json, String key, Consumer<String> validator) {
        if (!json.has(key)) return Set.of();
        Set<String> values = new HashSet<>();
        json.getAsJsonArray(key).forEach(element -> {
            String value = element.getAsString();
            validator.accept(value);
            if (!values.add(value)) throw new IllegalArgumentException("duplicate " + key + " entry: " + value);
        });
        return Set.copyOf(values);
    }
}
