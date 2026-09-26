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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class HopperFilterGroups {
    public record Group(
            String id,
            String name,
            Set<String> items,
            Set<String> potions,
            Set<String> enchantments
    ) {
        boolean matches(ItemStack stack) {
            if (items.contains(HopperFilter.baseItemId(stack))) return true;
            if (HopperFilter.potionId(stack).filter(potions::contains).isPresent()) return true;
            return HopperFilter.storedEnchantmentIds(stack).stream().anyMatch(enchantments::contains);
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
                        Set<String> enchantments = readIds(json, "enchantments", HopperFilterGroups::validateMinecraftId);
                        if (items.isEmpty() && potions.isEmpty() && enchantments.isEmpty()) {
                            throw new IllegalArgumentException("a group must contain items, potions, or enchantments");
                        }
                        if (loaded.put(id, new Group(id, name, items, potions, enchantments)) != null) {
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
        return groups.values().stream()
                .filter(group -> stacks.stream().allMatch(group::matches))
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
