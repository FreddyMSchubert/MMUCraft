package uk.co.httpsmmuminecraftsociety.mainmod.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingCatches;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishRarity;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MagicFoodEffects {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final String ACTIVATED_TAG = "mainmod_magic_food";

    public enum Effect {
        SUNDAY_MONEY("sunday_money", "money", 1),
        SHOP_20("shop_20", "shop", 20),
        SHOP_30("shop_30", "shop", 30),
        FASHION_BOOKS("fashion_books", "fashion_books", 1),
        CHARM_BOOKS("charm_books", "charm_books", 1),
        KNOWLEDGE_BOOKS("knowledge_books", "knowledge_books", 1),
        JOKE_BOOKS("joke_books", "joke_books", 1),
        FISH_COMMON("fish_common", "fish_common", 1),
        FISH_UNCOMMON("fish_uncommon", "fish_uncommon", 1),
        FISH_RARE("fish_rare", "fish_rare", 1),
        FISH_EPIC("fish_epic", "fish_epic", 1),
        FISH_LEGENDARY("fish_legendary", "fish_legendary", 1),
        FISH_MYTHICAL("fish_mythical", "fish_mythical", 1),
        FISH_UNCAUGHT("fish_uncaught", "fish_uncaught", 1);

        public final String id;
        public final String category;
        public final int strength;

        Effect(String id, String category, int strength) {
            this.id = id;
            this.category = category;
            this.strength = strength;
        }

        public static Effect byId(String id) {
            for (Effect effect : values()) if (effect.id.equals(id)) return effect;
            throw new IllegalArgumentException("Unknown magic effect: " + id);
        }
    }

    private static final Effect[] FISH_RARITY_EFFECTS = {
            Effect.FISH_COMMON, Effect.FISH_UNCOMMON, Effect.FISH_RARE,
            Effect.FISH_EPIC, Effect.FISH_LEGENDARY, Effect.FISH_MYTHICAL
    };

    public record Spec(Effect effect, int dust, String description) {
        public Spec {
            if (dust < 1 || dust > 8) throw new IllegalArgumentException("Magic dust cost must be 1 to 8");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("Magic effect description is required");
        }
    }

    private MagicFoodEffects() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("effects").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    List<Running> running = active(player);
                    player.sendSystemMessage(Component.literal(running.isEmpty()
                            ? "No magic food effects are active." : "Active effects:"));
                    for (Running effect : running) {
                        player.sendSystemMessage(Component.literal("- " + effect.description() + " (until midnight)"));
                    }
                    return 1;
                })));
    }

    public static ItemStack activate(FakeItem food) {
        Spec spec = food.magicEffect();
        if (spec == null) return ItemStack.EMPTY;
        ItemStack stack = food.createItemStack();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ACTIVATED_TAG, true));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Magic " + food.title()).withStyle(ChatFormatting.LIGHT_PURPLE));
        stack.set(DataComponents.LORE, new ItemLore(food.buildLore(true)));
        FakeItems.wrapTooltip(stack);
        return stack;
    }

    public static boolean isActivated(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(ACTIVATED_TAG, false);
    }

    public static void consumed(ServerPlayer player, ItemStack stack) {
        if (!isActivated(stack)) return;
        FakeItem food = FakeItems.getFakeItemFromStack(stack);
        if (food == null || food.magicEffect() == null) return;
        Spec spec = food.magicEffect();
        long now = System.currentTimeMillis();
        MasteryAdvancements.grant(player, "magic/eat");
        if (food.id().equals("food-marshmallows-roasted")
                && MasteryAdvancements.isCommitteeKillSunday(now) && AdvancementMoney.isSundayRewardDay())
            MasteryAdvancements.grant(player, "magic/sunday_marshmallows");
        long expiresAt = expiresAt(now);
        Data data = data(player);
        Effect current = data.effect(player.getUUID(), spec.effect().category, now);
        if (!canReplace(current, spec.effect())) {
            player.sendSystemMessage(Component.literal("Effect already active: " + description(current) + "."));
            return;
        }
        data.activate(player.getUUID(), spec.effect(), expiresAt);
        if (spec.effect() == Effect.FISH_UNCAUGHT) FishingCatches.refreshCaughtFish(player);
        player.sendSystemMessage(Component.literal("Activated " + spec.description() + " until midnight."));
    }

    static boolean canReplace(Effect current, Effect incoming) {
        return current == null || !current.category.equals(incoming.category)
                || (current != incoming && incoming.strength >= current.strength);
    }

    static long expiresAt(long unixMs) {
        return Instant.ofEpochMilli(unixMs).atZone(LONDON).toLocalDate().plusDays(1)
                .atStartOfDay(LONDON).toInstant().toEpochMilli();
    }

    public static boolean has(ServerPlayer player, Effect effect) {
        return data(player).has(player.getUUID(), effect, System.currentTimeMillis());
    }

    public static int fishRarityBoostMask(ServerPlayer player) {
        return data(player).fishRarityBoostMask(player.getUUID(), System.currentTimeMillis());
    }

    public static boolean wantedFish(FishRarity rarity, int boostMask, boolean uncaughtBoost, boolean firstCatch) {
        return (boostMask & (1 << rarity.ordinal())) != 0 || (uncaughtBoost && firstCatch);
    }

    public static int shopDiscount(ServerPlayer player) {
        Effect effect = data(player).effect(player.getUUID(), "shop", System.currentTimeMillis());
        return effect == null ? 0 : effect.strength;
    }

    public static boolean has(MinecraftServer server, UUID playerId, Effect effect) {
        return data(server).has(playerId, effect, System.currentTimeMillis());
    }

    public static int shopDiscount(MinecraftServer server, UUID playerId) {
        Effect effect = data(server).effect(playerId, "shop", System.currentTimeMillis());
        return effect == null ? 0 : effect.strength;
    }

    public static List<Running> active(ServerPlayer player) {
        return data(player).active(player.getUUID(), System.currentTimeMillis());
    }

    private static String description(Effect effect) {
        for (FakeItem item : FakeItems.ALL) {
            if (item.magicEffect() != null && item.magicEffect().effect() == effect)
                return item.magicEffect().description();
        }
        return effect.id.replace('_', ' ');
    }

    private static Data data(ServerPlayer player) {
        return data(player.level().getServer());
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    public record Running(Effect effect, String description, long expiresAt) {}

    private static final class Data extends SavedData {
        private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("effects").forGetter(data -> data.entries)
        ).apply(instance, Data::new));
        private static final SavedDataType<Data> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "magic_food_effects"), Data::new, CODEC, null);
        private final Map<String, Long> entries;

        private Data() { this(Map.of()); }
        private Data(Map<String, Long> entries) { this.entries = new HashMap<>(entries); }

        private Effect effect(UUID playerId, String category, long now) {
            for (Effect candidate : Effect.values()) {
                if (!candidate.category.equals(category)) continue;
                if (entries.getOrDefault(playerId + ":" + candidate.id, 0L) > now) return candidate;
            }
            return null;
        }

        private boolean has(UUID playerId, Effect effect, long now) {
            return entries.getOrDefault(playerId + ":" + effect.id, 0L) > now;
        }

        private int fishRarityBoostMask(UUID playerId, long now) {
            String prefix = playerId + ":";
            int mask = 0;
            for (int index = 0; index < FISH_RARITY_EFFECTS.length; index++) {
                if (entries.getOrDefault(prefix + FISH_RARITY_EFFECTS[index].id, 0L) > now)
                    mask |= 1 << index;
            }
            return mask;
        }

        private void activate(UUID playerId, Effect effect, long expiresAt) {
            for (Effect candidate : Effect.values()) {
                if (candidate.category.equals(effect.category)) entries.remove(playerId + ":" + candidate.id);
            }
            entries.put(playerId + ":" + effect.id, expiresAt);
            setDirty();
        }

        private List<Running> active(UUID playerId, long now) {
            List<Running> running = new ArrayList<>();
            for (Effect effect : Effect.values()) {
                long expiresAt = entries.getOrDefault(playerId + ":" + effect.id, 0L);
                if (expiresAt > now) running.add(new Running(effect, description(effect), expiresAt));
            }
            return running;
        }
    }
}
