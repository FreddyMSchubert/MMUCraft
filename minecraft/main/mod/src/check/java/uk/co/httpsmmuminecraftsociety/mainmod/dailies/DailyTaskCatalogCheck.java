package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureToggle;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.FeatureTogglesSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DailyTaskCatalogCheck {
    private DailyTaskCatalogCheck() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());
        var sharpness = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness, 2);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        assert DailyTaskRegistry.hasEnchantment(book, sharpness, 2);
        assert !DailyTaskRegistry.hasEnchantment(book, sharpness, 3);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        assert DailyTaskRegistry.hasEnchantment(sword, sharpness, 2);
        ItemStack beeNest = new ItemStack(Items.BEE_NEST);
        assert !DailyTaskCatalog.hasBees(beeNest);
        beeNest.set(DataComponents.BEES, new Bees(List.of(BeehiveBlockEntity.Occupant.create(0))));
        assert DailyTaskCatalog.hasBees(beeNest);
        Path project = Path.of(args[0]);
        Path advancementPolicy = project.resolve("build/resources/main/data/mainmod")
                .resolve(DailyAdvancementPolicy.RESOURCE_PATH);
        if (!Files.isRegularFile(advancementPolicy)) {
            throw new IllegalStateException("Missing packaged daily advancement policy: " + advancementPolicy);
        }
        DailyAdvancementPolicy.load(JsonParser.parseString(Files.readString(advancementPolicy)).getAsJsonObject());
        checkAdvancementDropPolicy();
        Set<String> fakeItemIds = new HashSet<>();
        try (var paths = Files.walk(project.resolve("../data/data/items"))) {
            for (Path path : paths.filter(file -> file.getFileName().toString().equals("item.json")).toList()) {
                String id = JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("id").getAsString();
                if (!fakeItemIds.add(id)) throw new IllegalStateException("Duplicate fake item id: " + id);
            }
        }

        var catalog = DailyTaskCatalog.load(
                project.resolve("src/main/resources").resolve(DailyTaskCatalog.RESOURCE_PATH),
                registries,
                fakeItemIds,
                DropCatalog.load(project.resolve("../data/drops.json"))
        );
        DailyTaskRegistry.validate(catalog);
        int taskCount = DailyTaskRegistry.optionCount(catalog);
        System.out.println("Daily task catalogue check passed: " + taskCount + " tasks in " + catalog.size() + " families.");
    }

    private static void checkAdvancementDropPolicy() {
        FeatureToggles.apply(FeatureTogglesSnapshot.getDefaultInstance());
        for (String id : new String[] {
                "blazeandcave:building/glowing", "blazeandcave:building/greek_art_decor",
                "blazeandcave:redstone/quartz_miner", "blazeandcave:redstone/quirky_quartz",
                "blazeandcave:monsters/poison_dart",
                "blazeandcave:monsters/the_ghastly_eyes", "blazeandcave:adventure/genesis_of_the_end",
                "minecraft:story/cure_zombie_villager"
        }) checkDrop(id, true);
        for (String id : new String[] {
                "blazeandcave:enchanting/complete_enchanter",
                "blazeandcave:enchanting/master_enchanter", "blazeandcave:challenges/ultimate_enchanter",
                "blazeandcave:challenges/the_world_is_ending", "blazeandcave:enchanting/master_armorer",
                "blazeandcave:enchanting/master_knight", "blazeandcave:enchanting/master_jouster",
                "blazeandcave:statistics/frequent_flyer", "blazeandcave:statistics/supersonic",
                "blazeandcave:adventure/disc_jockey", "blazeandcave:monsters/jockey_jamboree",
                "minecraft:husbandry/bred_all_animals",
                "minecraft:adventure/kill_all_mobs", "minecraft:husbandry/balanced_diet",
                "minecraft:nether/all_effects"
        }) checkDrop(id, false);

        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("nether").setEnabled(true)).build());
        checkDrop("blazeandcave:monsters/jockey_jamboree", true);
        checkDrop("blazeandcave:enchanting/complete_enchanter", true);
        checkDrop("blazeandcave:adventure/disc_jockey", true);
        checkDrop("minecraft:husbandry/bred_all_animals", true);
        checkDrop("blazeandcave:enchanting/master_armorer", false);
        checkDrop("blazeandcave:statistics/frequent_flyer", false);
        checkDrop("minecraft:adventure/kill_all_mobs", false);

        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("void").setEnabled(true)).build());
        checkDrop("blazeandcave:enchanting/master_armorer", false);
        checkDrop("minecraft:adventure/kill_all_mobs", false);

        FeatureToggles.apply(FeatureTogglesSnapshot.newBuilder()
                .addToggles(FeatureToggle.newBuilder().setKey("nether").setEnabled(true))
                .addToggles(FeatureToggle.newBuilder().setKey("void").setEnabled(true)).build());
        checkDrop("blazeandcave:enchanting/master_armorer", true);
        checkDrop("blazeandcave:enchanting/master_knight", true);
        checkDrop("blazeandcave:statistics/frequent_flyer", true);
        checkDrop("minecraft:adventure/kill_all_mobs", true);
        checkDrop("minecraft:husbandry/balanced_diet", true);
    }

    private static void checkDrop(String id, boolean expected) {
        Identifier advancement = Identifier.parse(id);
        Identifier root = Identifier.fromNamespaceAndPath(advancement.getNamespace(), advancement.getPath().split("/")[0] + "/root");
        if (DailyAdvancementPolicy.isDropUnlocked(advancement, root) != expected) {
            throw new IllegalStateException("Wrong drop gate for " + id + ": expected " + expected);
        }
    }
}
