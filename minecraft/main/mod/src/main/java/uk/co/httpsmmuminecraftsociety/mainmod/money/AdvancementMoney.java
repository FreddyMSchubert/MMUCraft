package uk.co.httpsmmuminecraftsociety.mainmod.money;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class AdvancementMoney {
    private static final String MOD_ID = "mainmod";
    private static final Identifier REWARD_RESOURCE =
            Identifier.fromNamespaceAndPath(MOD_ID, "money/advancement_dabloons.json");

    private static Map<String, Integer> advancementRewards = Map.of();

    private AdvancementMoney() {}

    public static void loadAdvancementRewards(ResourceManager resourceManager) {
        advancementRewards = loadAdvancementRewardsFromResource(resourceManager);
    }

    public static int moneyForAdvancement(Identifier advancementId, int experience) {
        if (advancementId != null) {
            Integer mappedReward = advancementRewards.get(advancementId.toString());
            if (mappedReward != null) return mappedReward;
        }

        return fallbackMoneyForExperience(experience);
    }

    private static int fallbackMoneyForExperience(int experience) {
        int money = (int) Math.ceil(((double) experience * (double) experience / 600.0D) + 5.0D);
        if (experience == 0) money = 3;
        return money;
    }

    private static Map<String, Integer> loadAdvancementRewardsFromResource(ResourceManager resourceManager) {
        try {
            Resource resource = resourceManager.getResource(REWARD_RESOURCE).orElse(null);
            if (resource == null) return Map.of();

            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                Map<String, Integer> rewards = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                        rewards.put(entry.getKey(), value.getAsInt());
                    }
                }

                return Map.copyOf(rewards);
            }
        } catch (IOException | JsonParseException | IllegalStateException ignored) {
            return Map.of();
        }
    }

    public static Component appendMoneyReward(Identifier advancementId, DisplayInfo displayInfo, int experience) {
        int money = moneyForAdvancement(advancementId, experience);
        return displayInfo.getDescription()
                .copy()
                .append(Component.literal("\nReward: " + money + " dabloons").withStyle(ChatFormatting.GOLD));
    }
}
