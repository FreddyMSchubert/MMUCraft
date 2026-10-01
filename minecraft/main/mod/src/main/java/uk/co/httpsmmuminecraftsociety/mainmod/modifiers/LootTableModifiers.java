package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettings;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.PickaxeHeaterCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonChestLoot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LootTableModifiers {
    private static final Identifier SNIFFER_DIGGING = Identifier.fromNamespaceAndPath("minecraft", "gameplay/sniffer_digging");
    private static final ZoneId MANCHESTER_TIME_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter SOUL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> CUSTOM_DISCS = List.of(
            "disc-9am", "disc-death", "disc-dog",
            "disc-droopy-likes-ricochet", "disc-droopy-likes-your-face"
    );
    private record LootAddition(
            Identifier tableId,
            String fakeItemId,
            Item vanillaItem,
            float chance,
            int minRolls,
            int maxRolls
    ) {
        public LootAddition {
            boolean hasFake = fakeItemId != null;
            boolean hasVanilla = vanillaItem != null;

            if (hasFake == hasVanilla) {
                throw new IllegalArgumentException(
                        "Exactly one of fakeItemId or vanillaItem must be set"
                );
            }
        }
    }
    private static final List<LootAddition> additions = List.of(
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/player"), null, Items.PLAYER_HEAD, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/enderman"), null, Items.ENDER_PEARL, 1.0F, 3, 10),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"), "charm-sculk-phial", null, 0.1F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"), "enderite-upgrade-smithing-template", null, 0.125F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/bat"), null, Items.PHANTOM_MEMBRANE, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.PHANTOM_MEMBRANE, 1.0F, 0, 15),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.DRAGON_EGG, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.DRAGON_HEAD, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/ender_dragon"), null, Items.ELYTRA, 1.0F, 1, 1),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/phantom"), null, Items.PHANTOM_MEMBRANE, 1.0F, 1, 2),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "chests/buried_treasure"), "trident-shaft", null, 1.0F, 2, 5),
            new LootAddition(Identifier.fromNamespaceAndPath("minecraft", "entities/elder_guardian"), "trident-prong", null, 1.0F, 1, 1)
    );

    private record PlayerLootAddition(
            List<String> playerNames,
            String fakeItemId,
            Item vanillaItem,
            float chance,
            int minRolls,
            int maxRolls
    ) {
        public PlayerLootAddition {
            boolean hasFake = fakeItemId != null;
            boolean hasVanilla = vanillaItem != null;

            if (hasFake == hasVanilla) {
                throw new IllegalArgumentException(
                        "Exactly one of fakeItemId or vanillaItem must be set"
                );
            }
        }

        public boolean matches(Player player) {
            String name = player.getGameProfile().name();

            return playerNames.stream()
                    .anyMatch(playerName -> playerName.equalsIgnoreCase(name));
        }
    }
    private static final List<PlayerLootAddition> playerAdditions = List.of(
            new PlayerLootAddition(List.of("archiedobbo"), null, Items.MANGROVE_DOOR, 1.0F, 1, 1),
            new PlayerLootAddition(List.of("archiedobbo"), "disc-9am", null, 1.0F, 1, 1),
            new PlayerLootAddition(List.of("oderzo"), null, Items.CUT_COPPER_STAIRS.waxed().weathered(), 1, 1, 3),
            new PlayerLootAddition(List.of("CalRay2"), "beer", null, 0.5f, 1, 5),
            new PlayerLootAddition(List.of("miaalicexoxo"), null, Items.LILY_OF_THE_VALLEY, 1, 1, 1),
            new PlayerLootAddition(List.of("MerlinSpace"), null, Items.APPLE, 1, 1, 1),
            new PlayerLootAddition(List.of("HannahLucyyy"), null, Items.BLUE_ORCHID, 0.2f, 1, 1)
    );
    private static void addPlayerSpecificDrops(
            Identifier tableId,
            LootContext lootContext,
            List<ItemStack> itemStacks
    ) {
        if (!Identifier.fromNamespaceAndPath("minecraft", "entities/player").equals(tableId)) {
            return;
        }

        Entity entity = lootContext.getOptional(LootContextParams.THIS_ENTITY);

        if (!(entity instanceof Player player)) {
            return;
        }

        for (PlayerLootAddition addition : playerAdditions) {
            if (!addition.matches(player)) continue;
            if (lootContext.getRandom().nextFloat() >= addition.chance()) continue;

            int rolls = Mth.nextInt(
                    lootContext.getRandom(),
                    addition.minRolls(),
                    addition.maxRolls()
            );

            if (rolls <= 0) continue;

            ItemStack stack;

            if (addition.fakeItemId() != null) {
                stack = FakeItems.ID_MAP.get(addition.fakeItemId()).createItemStack();
            } else {
                stack = addition.vanillaItem().getDefaultInstance();
            }

            stack.setCount(rolls);
            itemStacks.add(stack);
        }
    }

    public static void onModifyDrops(Holder<LootTable> lootTableHolder, LootContext lootContext, List<ItemStack> itemStacks) {
        itemStacks.removeIf(stack -> stack.is(Items.DIAMOND) && lootTableHolder.unwrapKey().get().identifier().toString().contains("chest"));

        Identifier tableId = lootTableHolder.unwrapKey().map(ResourceKey::identifier).orElse(null);

        PickaxeHeaterCharm.heatMinedDrops(lootContext, itemStacks);

        rerollMusicDiscs(lootTableHolder.value(), lootContext, itemStacks);
        addPlayerSpecificDrops(tableId, lootContext, itemStacks);
        DabloonChestLoot.addDrops(tableId, lootContext, itemStacks);
        UnlockBookLoot.addBookDrops(tableId, lootContext, itemStacks);
        addSnifferClover(tableId, lootContext, itemStacks);

        if (Identifier.fromNamespaceAndPath("minecraft", "entities/player").equals(tableId)
                && lootContext.getOptional(LootContextParams.THIS_ENTITY) instanceof Player victim) {
            DamageSource damageSource = lootContext.getOptional(LootContextParams.DAMAGE_SOURCE);
            String soulId = damageSource != null
                    && damageSource.getEntity() instanceof Player killer
                    && !killer.getUUID().equals(victim.getUUID())
                    ? "soul" : "soul-shard";
            int count = soulId.equals("soul-shard") ? Mth.nextInt(lootContext.getRandom(), 1, 4) : 1;
            itemStacks.add(FakeItems.createFakeItemStack(soulId, count));
        }

        for (LootAddition addition : additions) {
            if (!addition.tableId().equals(tableId)) continue;
            if (lootContext.getRandom().nextFloat() >= addition.chance()) continue;

            int rolls = Mth.nextInt(
                    lootContext.getRandom(),
                    addition.minRolls(),
                    addition.maxRolls()
            );

            ItemStack stack = ItemStack.EMPTY;
            if (addition.fakeItemId() != null)
                stack = FakeItems.ID_MAP.get(addition.fakeItemId()).createItemStack();
            else if (addition.vanillaItem() != null)
                stack = addition.vanillaItem().getDefaultInstance();

            if (stack.is(Items.PLAYER_HEAD)
                    && Identifier.fromNamespaceAndPath("minecraft", "entities/player").equals(tableId)
                    && lootContext.getOptional(LootContextParams.THIS_ENTITY) instanceof Player player) {
                String deathMessage = player.getCombatTracker().getDeathMessage().getString();
                String owner = player.getDisplayName().getString();

                String death = deathMessage.startsWith(owner)
                        ? deathMessage.substring(owner.length()).stripLeading()
                        : deathMessage;

                stack.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("This soul belonged to " + owner + ", who " + death + "."),
                        Component.literal("[RIP - " + ZonedDateTime.now(MANCHESTER_TIME_ZONE).format(SOUL_DATE_FORMAT) + "]")
                )));
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
                if (FeatureToggles.isEnabled(FeatureToggles.CIRCUS)) {
                    CustomData.update(DataComponents.CUSTOM_DATA, stack,
                            tag -> tag.putBoolean("mainmod_death_head", true));
                }
            }

            stack.setCount(rolls);
            itemStacks.add(stack);
        }

        for (Map.Entry<Identifier, List<EnchantmentSettings>> entry : EnchantmentSettingsManager.byLoottable.entrySet()) {
            if (!entry.getKey().equals(tableId)) continue;
            for (EnchantmentSettings enchType : entry.getValue()) {
                float chance;
                switch (enchType.loottableRarity) {
                    case Rarity.UNCOMMON -> chance = 0.5f;
                    case Rarity.RARE -> chance = 0.25f;
                    case Rarity.EPIC -> chance = 0.125f;
                    default -> chance = 1.0f;
                }
                if (enchType.loottableChance >= 0.0f) chance = enchType.loottableChance;
                if (lootContext.getRandom().nextFloat() >= chance) continue;

                ItemStack stack = Items.ENCHANTED_BOOK.getDefaultInstance();
                Holder<Enchantment> enchantmentHolder = lootContext.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(enchType.enchantment);
                ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                enchantments.set(enchantmentHolder, Mth.nextInt(lootContext.getRandom(), 1, enchType.maxNormalGearLevel));
                stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());

                // Brushing keeps only the first archaeology loot result. Replace it instead of
                // appending a second stack that BrushableBlockEntity would discard.
                if (enchType.enchantment.equals(ModEnchantments.CHARM_BOOST)) {
                    itemStacks.clear();
                }
                itemStacks.add(stack);
            }
        }
        itemStacks.forEach(GearEnchantmentSanitizer::sanitize);
    }

    private static void rerollMusicDiscs(LootTable table, LootContext context, List<ItemStack> drops) {
        List<Item> vanillaDiscs = null;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (!drop.getItem().getDefaultInstance().has(DataComponents.JUKEBOX_PLAYABLE)) continue;
            if (vanillaDiscs == null) {
                Set<Item> candidates = new HashSet<>();
                LootTable.DIRECT_CODEC.encodeStart(
                        RegistryOps.create(JsonOps.INSTANCE, context.getLevel().registryAccess()), table
                ).result().ifPresent(json -> collectMusicDiscs(json, context, candidates, new HashSet<>()));
                candidates.add(drop.getItem());
                vanillaDiscs = new ArrayList<>(candidates);
                vanillaDiscs.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)));
            }

            int choice = context.getRandom().nextInt(vanillaDiscs.size() + CUSTOM_DISCS.size());
            ItemStack replacement = choice < vanillaDiscs.size()
                    ? vanillaDiscs.get(choice).getDefaultInstance()
                    : FakeItems.createFakeItemStack(CUSTOM_DISCS.get(choice - vanillaDiscs.size()), 1);
            replacement.setCount(drop.getCount());
            drops.set(i, replacement);
        }
    }

    private static void collectMusicDiscs(JsonElement json, LootContext context, Set<Item> discs, Set<Identifier> visited) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(entry -> collectMusicDiscs(entry, context, discs, visited));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
                BuiltInRegistries.ITEM.getOptional(Identifier.parse(object.get("name").getAsString()))
                        .filter(item -> item.getDefaultInstance().has(DataComponents.JUKEBOX_PLAYABLE))
                        .ifPresent(discs::add);
            } else if (object.has("type") && object.get("type").getAsString().equals("minecraft:tag") && object.has("items")) {
                String id = object.get("items").getAsString();
                if (id.startsWith("#")) {
                    context.getLevel().registryAccess().lookupOrThrow(Registries.ITEM)
                            .get(TagKey.create(Registries.ITEM, Identifier.parse(id.substring(1))))
                            .ifPresent(tag -> tag.forEach(holder -> {
                                Item item = holder.value();
                                if (item.getDefaultInstance().has(DataComponents.JUKEBOX_PLAYABLE)) discs.add(item);
                            }));
                }
            } else if (object.has("type") && object.get("type").getAsString().equals("minecraft:loot_table")
                    && object.has("value") && object.get("value").isJsonPrimitive()) {
                Identifier id = Identifier.parse(object.get("value").getAsString());
                if (visited.add(id)) {
                    LootTable nested = context.getLevel().getServer().reloadableRegistries()
                            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
                    LootTable.DIRECT_CODEC.encodeStart(
                            RegistryOps.create(JsonOps.INSTANCE, context.getLevel().registryAccess()), nested
                    ).result().ifPresent(value -> collectMusicDiscs(value, context, discs, visited));
                }
            }
            object.entrySet().forEach(entry -> collectMusicDiscs(entry.getValue(), context, discs, visited));
        }
    }

    private static void addSnifferClover(Identifier tableId, LootContext context, List<ItemStack> drops) {
        if (!SNIFFER_DIGGING.equals(tableId)) return;

        // This is additive: vanilla seeds keep their original odds instead of competing with clovers.
        float roll = context.getRandom().nextFloat();
        String cloverId = roll < 0.01F ? "4-leaf-clover"
                : roll < 0.05F ? "3-leaf-clover"
                : roll < 0.20F ? "2-leaf-clover"
                : null;
        if (cloverId != null) {
            drops.add(FakeItems.createFakeItemStack(cloverId, 1));
        }
    }
}
