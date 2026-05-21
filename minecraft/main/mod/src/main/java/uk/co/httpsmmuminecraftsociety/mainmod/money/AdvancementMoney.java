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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class AdvancementMoney {
    private static final String MOD_ID = "mainmod";
    private static final Identifier REWARD_RESOURCE =
            Identifier.fromNamespaceAndPath(MOD_ID, "money/advancement_dabloons.jsonc");

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

            try (var inputStream = resource.open()) {
                String jsonc = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(stripJsonComments(jsonc)).getAsJsonObject();

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

    private static String stripJsonComments(String jsonc) {
        StringBuilder json = new StringBuilder(jsonc.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < jsonc.length(); i++) {
            char current = jsonc.charAt(i);
            char next = i + 1 < jsonc.length() ? jsonc.charAt(i + 1) : '\0';

            if (inString) {
                json.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                json.append(current);
                continue;
            }

            if (current == '/' && next == '/') {
                while (i < jsonc.length() && jsonc.charAt(i) != '\n') {
                    i++;
                }
                if (i < jsonc.length()) json.append('\n');
                continue;
            }

            if (current == '/' && next == '*') {
                i += 2;
                while (i + 1 < jsonc.length() && !(jsonc.charAt(i) == '*' && jsonc.charAt(i + 1) == '/')) {
                    if (jsonc.charAt(i) == '\n') json.append('\n');
                    i++;
                }
                i++;
                continue;
            }

            json.append(current);
        }

        return json.toString();
    }

    public static Component appendMoneyReward(Identifier advancementId, DisplayInfo displayInfo, int experience) {
        int money = moneyForAdvancement(advancementId, experience);
        return displayInfo.getDescription()
                .copy()
                .append(Component.literal("\nReward: " + money + " dabloons").withStyle(ChatFormatting.GOLD));
    }
}
