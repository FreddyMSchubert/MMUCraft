package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class VillagerTradeTask extends CountedTask {
    private final Mode mode;
    private final String target;
    private final String targetName;

    private VillagerTradeTask(Mode mode, String target, String targetName, int min, int max, double rewardPerIteration) {
        super("trade:" + mode.id + (target.isBlank() ? "" : ":" + target), mode.emoji, min, max, rewardPerIteration,
                mode.progressLabel, mode.progressUnit);
        this.mode = mode;
        this.target = target;
        this.targetName = targetName;
    }

    public VillagerTradeTask(Mode mode, int min, int max, double rewardPerIteration) {
        this(mode, "", "", min, max, rewardPerIteration);
        if (mode != Mode.RECEIVE_EMERALDS && mode != Mode.SPEND_EMERALDS) {
            throw new IllegalArgumentException("This villager trade mode requires a target");
        }
    }

    public VillagerTradeTask(Item item, String itemName, int min, int max, double rewardPerIteration) {
        this(Mode.RECEIVE_ITEM, DailyTargetId.of(item), itemName, min, max, rewardPerIteration);
    }

    public static VillagerTradeTask give(Item item, String itemName, int min, int max, double rewardPerIteration) {
        return new VillagerTradeTask(Mode.GIVE_ITEM, DailyTargetId.of(item), itemName, min, max, rewardPerIteration);
    }

    public static VillagerTradeTask wanderingTrader(int min, int max, double rewardPerIteration) {
        return new VillagerTradeTask(Mode.WANDERING_TRADER, "", "", min, max, rewardPerIteration);
    }

    public VillagerTradeTask(ResourceKey<VillagerProfession> profession, String professionName, int min, int max,
                             double rewardPerIteration) {
        this(Mode.PROFESSION, DailyTargetId.of(profession), professionName, min, max, rewardPerIteration);
    }

    @Override protected String name(int count) {
        return switch (mode) {
            case RECEIVE_EMERALDS -> "Emerald Exporter";
            case SPEND_EMERALDS -> "Support Local Villagers";
            case RECEIVE_ITEM -> "Trade for " + targetName;
            case GIVE_ITEM -> "Trade Away " + targetName;
            case PROFESSION -> "Visit a " + targetName;
            case WANDERING_TRADER -> "A Wandering Deal";
        };
    }

    @Override protected String description(int count) {
        return switch (mode) {
            case RECEIVE_EMERALDS -> "Receive " + count + " emeralds from villager trades.";
            case SPEND_EMERALDS -> "Spend " + count + " emeralds in villager trades.";
            case RECEIVE_ITEM -> "Receive " + count + " " + targetName.toLowerCase() + " from villager trades.";
            case GIVE_ITEM -> "Give villagers " + count + " " + targetName.toLowerCase() + " in trades.";
            case PROFESSION -> "Complete " + count + " trades with a " + targetName.toLowerCase() + ".";
            case WANDERING_TRADER -> "Complete " + count + " trades with a wandering trader before they wander off.";
        };
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        if (event.type() != DailyTaskEvent.Type.VILLAGER_TRADE) return false;
        return switch (mode) {
            case RECEIVE_EMERALDS -> event.subject().equals("receive_emeralds");
            case SPEND_EMERALDS -> event.subject().equals("spend_emeralds");
            case RECEIVE_ITEM -> event.subject().equals("receive_item") && event.secondary().equals(target);
            case GIVE_ITEM -> event.subject().equals("give_item") && event.secondary().equals(target);
            case PROFESSION -> event.subject().equals("profession") && event.secondary().equals(target);
            case WANDERING_TRADER -> event.subject().equals("wandering_trader");
        };
    }

    @Override protected void addTaskData(JsonObject task, java.util.Random random) {
        task.addProperty("tradeMode", mode.id);
        if (!target.isBlank()) task.addProperty("target", target);
    }

    public enum Mode {
        RECEIVE_EMERALDS("receive_emeralds", "💚", "Received", "emeralds"),
        SPEND_EMERALDS("spend_emeralds", "🛒", "Spent", "emeralds"),
        RECEIVE_ITEM("receive_item", "📦", "Received", "items"),
        GIVE_ITEM("give_item", "🤝", "Traded", "items"),
        PROFESSION("profession", "🧑‍🌾", "Trades", "completed"),
        WANDERING_TRADER("wandering_trader", "🦙", "Trades", "completed");

        private final String id;
        private final String emoji;
        private final String progressLabel;
        private final String progressUnit;

        Mode(String id, String emoji, String progressLabel, String progressUnit) {
            this.id = id;
            this.emoji = emoji;
            this.progressLabel = progressLabel;
            this.progressUnit = progressUnit;
        }
    }
}
