package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

public interface DailyTaskDefinition {
    String getId();

    JsonObject create(int count);

    int getReward(JsonObject task);

    default int progress(JsonObject task, DailyTaskEvent event) {
        return 0;
    }

    default ClaimResult claim(ServerPlayer player, JsonObject task) {
        int max = task.get("max").getAsInt();
        if (max > 0 && task.get("current").getAsInt() < max) {
            return ClaimResult.failure("Complete this daily in-game first.");
        }

        int reward = Math.max(0, getReward(task));
        if (!MoneyHelper.GainMoney(player, reward)) {
            return ClaimResult.failure("Could not grant the daily reward.");
        }
        return ClaimResult.success("Daily claimed for " + reward + " dabloons.");
    }

    record ClaimResult(boolean claimed, String message) {
        public static ClaimResult success(String message) {
            return new ClaimResult(true, message);
        }

        public static ClaimResult failure(String message) {
            return new ClaimResult(false, message);
        }
    }
}
