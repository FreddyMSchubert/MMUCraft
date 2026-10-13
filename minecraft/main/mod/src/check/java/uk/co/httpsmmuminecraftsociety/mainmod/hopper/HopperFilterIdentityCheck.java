package uk.co.httpsmmuminecraftsociety.mainmod.hopper;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomModelData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfResonanceCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HopperFilterIdentityCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());

        int charmId = 9999;
        FakeItem fakeCharm = new FakeItem("Test Charm", "test-charm", Rarity.COMMON,
                1, false, false, "", List.of(), Items.HEART_OF_THE_SEA, List.of());
        FakeItem fakeItem = new FakeItem("Test Item", "test-item", Rarity.COMMON,
                1, false, false, "", List.of(), Items.STICK, List.of());
        FakeItems.ID_MAP = Map.of(fakeCharm.id(), fakeCharm, fakeItem.id(), fakeItem);
        FakeItems.CHARM_ID_MAP = Map.of(charmId, fakeCharm);

        ItemStack plainArmor = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack charmedArmor = plainArmor.copy();
        CharmStackData.setStoredCharms(charmedArmor, List.of(new StoredCharmData(charmId, 1)));
        charmedArmor.set(DataComponents.CUSTOM_NAME, Component.literal("A different name"));

        var ironArmor = new HopperFilterGroups.Group("armor_iron", "Iron Armor",
                Set.of("minecraft:iron_chestplate"), Set.of(), Set.of(), Set.of());
        assert ironArmor.matches(plainArmor);
        assert ironArmor.matches(charmedArmor);
        assert HopperFilter.baseItemId(charmedArmor).equals(HopperFilter.baseItemId(plainArmor));
        assert PotionOfResonanceCharm.matchesItemType(charmedArmor, plainArmor);
        assert PotionOfResonanceCharm.matchesItemType(plainArmor, charmedArmor);

        ItemStack differentArmor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        CharmStackData.setStoredCharms(differentArmor, List.of(new StoredCharmData(charmId, 1)));
        assert !PotionOfResonanceCharm.matchesItemType(differentArmor, charmedArmor);
        assert !PotionOfResonanceCharm.matchesItemType(ItemStack.EMPTY, charmedArmor);

        ItemStack standaloneCharm = new ItemStack(Items.HEART_OF_THE_SEA);
        CharmStackData.setStoredCharms(standaloneCharm, List.of(new StoredCharmData(charmId, 1)));
        standaloneCharm.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(fakeCharm.id()), List.of()));
        assert HopperFilter.baseItemId(standaloneCharm).equals("mainmod:test-charm");
        assert !ironArmor.matches(standaloneCharm);
        assert !PotionOfResonanceCharm.matchesItemType(standaloneCharm, charmedArmor);

        ItemStack charmedFakeItem = fakeItem.createItemStack();
        CharmStackData.setStoredCharms(charmedFakeItem, List.of(new StoredCharmData(charmId, 1)));
        assert HopperFilter.baseItemId(charmedFakeItem).equals("mainmod:test-item");
        assert !PotionOfResonanceCharm.matchesItemType(charmedFakeItem, standaloneCharm);

        var goodNormalPotions = new HopperFilterGroups.Group("good_normal_potions", "Beneficial Drinkable Potions",
                Set.of(), Set.of("minecraft:swiftness"), Set.of("minecraft:potion"), Set.of());
        var normalPotions = new HopperFilterGroups.Group("normal_potions", "Normal Potions",
                Set.of("minecraft:potion"), Set.of(), Set.of(), Set.of());
        assert goodNormalPotions.matches(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        assert !goodNormalPotions.matches(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS));
        assert !goodNormalPotions.matches(PotionContents.createItemStack(Items.LINGERING_POTION, Potions.SWIFTNESS));
        assert !goodNormalPotions.matches(PotionContents.createItemStack(Items.POTION, Potions.POISON));
        assert goodNormalPotions.matchBreadth(48) < normalPotions.matchBreadth(48);

        List<String> unknownItems = new ArrayList<>();
        try (var paths = Files.list(Path.of(args[0]))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                var group = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (group.has("potion_items")) {
                    assert group.has("potions") : path + " has potion_items without potions";
                    for (var item : group.getAsJsonArray("potion_items")) {
                        assert Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion")
                                .contains(item.getAsString()) : path + " has invalid potion item " + item;
                    }
                }
                if (!group.has("items")) continue;
                for (var item : group.getAsJsonArray("items")) {
                    String id = item.getAsString();
                    if (id.startsWith("minecraft:")) {
                        if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))) {
                            unknownItems.add(path.getFileName() + ": " + id);
                        }
                    }
                }
            }
        }
        assert unknownItems.isEmpty() : "Unknown group items: " + unknownItems;

        Set<String> bucketItems = groupItems(Path.of(args[0], "buckets.json"));
        Set<String> storageItems = groupItems(Path.of(args[0], "storage.json"));
        var missingBuckets = BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .filter(id -> id.equals("minecraft:bucket") || id.endsWith("_bucket"))
                .filter(id -> !bucketItems.contains(id))
                .toList();
        var missingCopperChests = BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .filter(id -> id.endsWith("copper_chest"))
                .filter(id -> !storageItems.contains(id))
                .toList();
        assert missingBuckets.isEmpty() : "Buckets group is missing: " + missingBuckets;
        assert missingCopperChests.isEmpty() : "Storage group is missing: " + missingCopperChests;

        System.out.println("Hopper filter identity checks passed.");
    }

    private static Set<String> groupItems(Path path) throws Exception {
        Set<String> items = new HashSet<>();
        var group = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (var item : group.getAsJsonArray("items")) items.add(item.getAsString());
        return items;
    }
}
