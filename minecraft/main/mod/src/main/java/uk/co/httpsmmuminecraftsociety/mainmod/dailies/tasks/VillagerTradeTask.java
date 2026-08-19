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
    private VillagerTradeTask(Mode mode, String target) {
        super("trade:" + mode.id + (target.isBlank() ? "" : ":" + target), mode.progressLabel, mode.progressUnit);
        this.mode = mode;
        this.target = target;
    }

    public VillagerTradeTask(Mode mode) {
        this(mode, "");
        if (mode != Mode.RECEIVE_EMERALDS && mode != Mode.SPEND_EMERALDS) {
            throw new IllegalArgumentException("This villager trade mode requires a target");
        }
    }

    public VillagerTradeTask(Item item) {
        this(Mode.RECEIVE_ITEM, DailyTargetId.of(item));
    }

    public static VillagerTradeTask give(Item item) {
        return new VillagerTradeTask(Mode.GIVE_ITEM, DailyTargetId.of(item));
    }

    public static VillagerTradeTask wanderingTrader() {
        return new VillagerTradeTask(Mode.WANDERING_TRADER, "");
    }

    public VillagerTradeTask(ResourceKey<VillagerProfession> profession) {
        this(Mode.PROFESSION, DailyTargetId.of(profession));
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

    @Override protected void addTaskData(JsonObject task) {
        task.addProperty("tradeMode", mode.id);
        if (!target.isBlank()) task.addProperty("target", target);
    }

    public enum Mode {
        RECEIVE_EMERALDS("receive_emeralds", "Received", "emeralds"),
        SPEND_EMERALDS("spend_emeralds", "Spent", "emeralds"),
        RECEIVE_ITEM("receive_item", "Received", "items"),
        GIVE_ITEM("give_item", "Traded", "items"),
        PROFESSION("profession", "Trades", "completed"),
        WANDERING_TRADER("wandering_trader", "Trades", "completed");

        private final String id;
        private final String progressLabel;
        private final String progressUnit;

        Mode(String id, String progressLabel, String progressUnit) {
            this.id = id;
            this.progressLabel = progressLabel;
            this.progressUnit = progressUnit;
        }
    }
}
