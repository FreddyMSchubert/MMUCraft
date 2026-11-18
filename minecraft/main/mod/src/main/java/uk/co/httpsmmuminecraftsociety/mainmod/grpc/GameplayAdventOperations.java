package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class GameplayAdventOperations {
    private GameplayAdventOperations() {}

    static GetAdventRewardsResponse getRewardsOnMainThread() {
        var response = GetAdventRewardsResponse.newBuilder();
        for (int day = 1; day <= 24; day++) {
            Gift gift = gift(day, false);
            var preview = AdventRewardPreview.newBuilder().setDay(day)
                    .setTitle(gift.title()).setBookText(gift.bookText());
            for (ItemStack item : gift.items()) preview.addItems(previewItem(item));
            if (day == 6) {
                Gift alternate = gift(day, true);
                preview.setAlternateBookText(alternate.bookText());
                for (ItemStack item : alternate.items()) preview.addAlternateItems(previewItem(item));
            }
            response.addRewards(preview);
        }
        return response.build();
    }

    static GrantAdventRewardResponse grantOnMainThread(GrantAdventRewardRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) return rejected("Join the Minecraft server to open this present.");
        if (request.getDay() < 1 || request.getDay() > 24 || request.getClaimId().isBlank()
                || request.getAmountDabloons() < 0 || request.getAmountDabloons() > 400
                || (request.getPreview() && request.getAmountDabloons() != 0))
            return rejected("The Advent request is invalid.");

        Data data = server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
        String receipt = player.getUUID() + ":" + request.getClaimId();
        Integer previousBalance = request.getPreview() ? null : data.delivered.get(receipt);
        if (previousBalance != null) return GrantAdventRewardResponse.newBuilder()
                .setAlreadyGranted(true).setBalanceDabloons(previousBalance)
                .setMessage("This present was already delivered.").build();

        Gift gift = gift(request.getDay(), request.getDay() == 6 && ThreadLocalRandom.current().nextBoolean());
        List<ItemStack> contents = new ArrayList<>(gift.items());
        contents.add(book(request.getDay(), gift.title(), gift.bookText()));
        ItemStack backpack = FakeItems.createFakeItemStack("charm-leather-backpack", 1);
        backpack.set(DataComponents.CUSTOM_NAME, Component.literal("Advent Day " + request.getDay()));
        backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        player.getInventory().add(backpack);
        if (!backpack.isEmpty() && DabloonInteractionLog.drop(player, backpack) == null)
            return rejected("The present could not be placed in your inventory or nearby.");
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        int amount = request.getAmountDabloons();
        if (amount > 0) {
            if (!MoneyHelper.GainMoney(player, amount))
                throw new IllegalStateException("Advent gift delivered, but Dabloon payment failed");
            MoneyHelper.SendBalanceMessage(player, amount, "Advent streak");
        }
        int balance = MoneyHelper.GetBalance(player);
        if (!request.getPreview()) {
            data.delivered.put(receipt, balance);
            data.setDirty();
        }
        return GrantAdventRewardResponse.newBuilder().setGranted(true).setBalanceDabloons(balance)
                .setMessage("Advent Day " + request.getDay() + ": " + gift.title() + " was delivered.")
                .build();
    }

    private static GrantAdventRewardResponse rejected(String message) {
        return GrantAdventRewardResponse.newBuilder().setMessage(message).build();
    }

    private static AdventRewardItem previewItem(ItemStack stack) {
        return AdventRewardItem.newBuilder()
                .setItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .setCount(stack.getCount()).build();
    }

    private static ItemStack book(int day, String title, String text) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Advent Day " + day), "MMUCraft", 0,
                List.of(Filterable.passThrough((Component) Component.literal(title + "\n\n" + text))), true));
        return stack;
    }

    private record Gift(String title, String bookText, List<ItemStack> items) {}

    private static Gift gift(int day, boolean alternate) {
        return switch (day) {
            case 1 -> gift("Light the first candle", "The long wait starts here. Plant a tree and put a light beside it.",
                    stack(Items.SPRUCE_SAPLING, 4), stack(Items.BONE_MEAL, 16), stack("red_candle", 4));
            case 2 -> gift("Snowball delivery", "A snowball is a letter with poor handwriting. Send one to a friend.",
                    stack(Items.SNOWBALL, 32), stack(Items.SNOW_BLOCK, 8), stack(Items.LEATHER_BOOTS, 1));
            case 3 -> gift("The miner's stocking", "Someone filled this stocking underground. At least the torches are useful.",
                    stack(Items.COAL, 16), stack(Items.TORCH, 32), stack(Items.IRON_PICKAXE, 1));
            case 4 -> gift("A tree with no room", "Take this little forest home. The shears are for when it gets out of hand.",
                    stack(Items.SPRUCE_LOG, 16), stack(Items.SPRUCE_LEAVES, 16), stack(Items.SHEARS, 1));
            case 5 -> gift("Leave a snack out", "Saint Nicholas visits tomorrow. Leave a cookie out, if you trust the foxes.",
                    stack(Items.COOKIE, 12), stack(Items.MILK_BUCKET, 2), stack(Items.HONEY_BOTTLE, 2));
            case 6 -> alternate
                    ? gift("Saint Nicholas approves", "On 6 December, many families celebrate Saint Nicholas. Good children may find a treat in their shoes. You found apples. Put your shoes somewhere safe next year.",
                            stack(Items.APPLE, 12), stack(Items.SPRUCE_LEAVES, 8), stack("red_candle", 4))
                    : gift("Saint Nicholas has notes", "On 6 December, many families celebrate Saint Nicholas. Some stories say naughty children get coal in their shoes. You got coal. It is still useful, so the joke is on Saint Nicholas.",
                            stack(Items.COAL, 16), stack(Items.SPRUCE_LEAVES, 8), stack("red_candle", 4));
            case 7 -> gift("String lights", "These lights need a builder. The redstone is yours to arrange.",
                    stack(Items.LANTERN, 8), stack(Items.REDSTONE, 24), stack(Items.STRING, 16));
            case 8 -> gift("A visitor from the pines", "Give the fox some berries. It may still steal your things.",
                    stack(Items.FOX_SPAWN_EGG, 1), stack(Items.SWEET_BERRIES, 24), stack(Items.LEAD, 2));
            case 9 -> gift("Build a snow guard", "It is brave. It is made of snow. Keep it away from the fireplace.",
                    stack(Items.CARVED_PUMPKIN, 2), stack(Items.SNOW_BLOCK, 24), stack(Items.IRON_SHOVEL, 1));
            case 10 -> gift("Cave rescue kit", "If you get lost, follow the torches back. That is why you brought a compass too.",
                    stack(Items.TORCH, 64), stack(Items.BREAD, 16), stack(Items.COMPASS, 1));
            case 11 -> gift("Cake for breakfast", "There are no rules about when to eat cake. There may be consequences.",
                    stack(Items.CAKE, 1), stack(Items.COOKIE, 8), stack(Items.COCOA_BEANS, 16));
            case 12 -> gift("Rehearsal at dusk", "These rockets are for a show. Pick a safe place with open sky.",
                    fireworkRocket(24, FireworkExplosion.Shape.STAR, 0xD33131, 0x30A451),
                    fireworkStar(8, FireworkExplosion.Shape.STAR, 0xD33131, 0x30A451), stack("red_dye", 8));
            case 13 -> gift("The second wreath", "The next Sunday needs a wreath. Build one with leaves, lights, and your own idea.",
                    stack(Items.SPRUCE_LEAVES, 24), stack("red_candle", 8), stack(Items.ITEM_FRAME, 4));
            case 14 -> gift("Music from the cave", "Put a record on while you mine. The cave may sing back.",
                    stack(Items.MUSIC_DISC_CAT, 1), stack(Items.JUKEBOX, 1), stack(Items.NOTE_BLOCK, 8));
            case 15 -> gift("Banner workshop", "A banner tells everyone where the party is. Make a design you can spot in snow.",
                    stack("red_banner", 2), stack("white_dye", 16), stack(Items.LOOM, 1));
            case 16 -> gift("Underwater stars", "The sea has its own winter lights. Bring a way to breathe.",
                    stack(Items.SEA_LANTERN, 8), stack(Items.GLOW_INK_SAC, 8),
                    PotionContents.createItemStack(Items.POTION, Potions.LONG_WATER_BREATHING));
            case 17 -> gift("A very old present", "The sniffer egg waited a long time. Give it a good place to hatch.",
                    stack(Items.SNIFFER_EGG, 1), stack(Items.TORCHFLOWER_SEEDS, 4), stack(Items.BONE_MEAL, 16));
            case 18 -> gift("Winter feast", "Invite someone over. The cake is easier to share than the carrots.",
                    stack(Items.CAKE, 1), stack(Items.COOKED_BEEF, 16), stack(Items.GOLDEN_CARROT, 16));
            case 19 -> gift("Two noisy guests", "A chicken and a sheep arrived early. They both think they are the present.",
                    stack(Items.CHICKEN_SPAWN_EGG, 1), stack(Items.SHEEP_SPAWN_EGG, 1), stack(Items.LEAD, 4));
            case 20 -> gift("The last wreath", "It is the final Sunday before Christmas. Make the doorway look ready.",
                    stack(Items.SPRUCE_LEAVES, 24), stack(Items.GLOWSTONE, 8), stack(Items.EXPERIENCE_BOTTLE, 16));
            case 21 -> gift("The long way home", "A compass and a spyglass can show the way. A map can tell the story.",
                    stack(Items.COMPASS, 1), stack(Items.SPYGLASS, 1), stack(Items.MAP, 4));
            case 22 -> gift("Stars over spawn", "Take the rockets outside. Do not point them at the tree.",
                    fireworkRocket(32, FireworkExplosion.Shape.BURST, 0x5F8CE6, 0xF9FFFF),
                    fireworkStar(12, FireworkExplosion.Shape.BURST, 0x5F8CE6, 0xF9FFFF), stack(Items.GLOWSTONE_DUST, 16));
            case 23 -> gift("One more sleep", "Keep this for tomorrow, or use it now. You have made it this far.",
                    stack(Items.DIAMOND, 4), stack(Items.EMERALD, 8), stack(Items.EXPERIENCE_BOTTLE, 24));
            case 24 -> gift("Christmas morning", "You opened every door. Keep the apple for a day that deserves it. Merry Christmas.",
                    stack(Items.ENCHANTED_GOLDEN_APPLE, 1), stack(Items.DIAMOND_BLOCK, 1), stack(Items.EXPERIENCE_BOTTLE, 32));
            default -> throw new IllegalArgumentException("Advent day must be between 1 and 24");
        };
    }

    private static Gift gift(String title, String bookText, ItemStack... items) {
        return new Gift(title, bookText, List.of(items));
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static ItemStack stack(String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + itemId));
        if (item == Items.AIR) throw new IllegalStateException("Unknown Advent item: " + itemId);
        return stack(item, count);
    }

    private static FireworkExplosion explosion(FireworkExplosion.Shape shape, int first, int second) {
        return new FireworkExplosion(shape, IntList.of(first, second), IntList.of(0xFFFFFF), true, true);
    }

    private static ItemStack fireworkRocket(int count, FireworkExplosion.Shape shape, int first, int second) {
        ItemStack rocket = stack(Items.FIREWORK_ROCKET, count);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion(shape, first, second))));
        return rocket;
    }

    private static ItemStack fireworkStar(int count, FireworkExplosion.Shape shape, int first, int second) {
        ItemStack star = stack(Items.FIREWORK_STAR, count);
        star.set(DataComponents.FIREWORK_EXPLOSION, explosion(shape, first, second));
        return star;
    }

    private static final class Data extends SavedData {
        private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("delivered").forGetter(data -> data.delivered)
        ).apply(instance, Data::new));
        private static final SavedDataType<Data> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "advent_deliveries"), Data::new, CODEC, null);
        private final Map<String, Integer> delivered;

        private Data() { this(Map.of()); }
        private Data(Map<String, Integer> delivered) { this.delivered = new HashMap<>(delivered); }
    }
}
