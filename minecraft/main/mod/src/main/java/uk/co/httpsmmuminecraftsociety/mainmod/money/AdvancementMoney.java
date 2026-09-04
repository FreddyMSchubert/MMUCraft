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
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public final class AdvancementMoney {
    private static final String MOD_ID = "mainmod";
    private static final Identifier REWARD_RESOURCE =
            Identifier.fromNamespaceAndPath(MOD_ID, "money/advancement_dabloons.jsonc");
    private static final ZoneId REWARD_TIME_ZONE = ZoneId.of("Europe/London");
    private static final int REWARD_DAY_RESET_HOUR = 4;

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

        return 0;
    }

    public static RewardCalculation rewardForAdvancement(
            Identifier advancementId,
            int experience,
            boolean isMember
    ) {
        return calculateReward(moneyForAdvancement(advancementId, experience), isSundayRewardDay(), isMember);
    }

    public static RewardCalculation calculateReward(int baseReward, boolean isSunday, boolean isMember) {
        int safeBaseReward = Math.max(0, baseReward);
        long scaledReward = (long) safeBaseReward
                * (isSunday ? 6L : 5L)
                * (isMember ? 6L : 5L);
        int totalReward = (int) Math.min(Integer.MAX_VALUE, (scaledReward + 24L) / 25L);
        return new RewardCalculation(safeBaseReward, isSunday, isMember, totalReward);
    }

    public static boolean isSundayRewardDay() {
        return ZonedDateTime.now(REWARD_TIME_ZONE)
                .minusHours(REWARD_DAY_RESET_HOUR)
                .getDayOfWeek() == DayOfWeek.SUNDAY;
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

    public static Component appendMoneyReward(
            Identifier advancementId,
            DisplayInfo displayInfo,
            int experience,
            boolean isMember
    ) {
        RewardCalculation reward = rewardForAdvancement(advancementId, experience, isMember);
        if (reward.baseReward() == 0) {
            return displayInfo.getDescription()
                    .copy()
                    .append(MoneyHelper.ReplaceDabloonWords("\n\nNo Dabloon reward")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return displayInfo.getDescription()
                .copy()
                .append(Component.literal("\nBase reward: ").withStyle(ChatFormatting.GRAY))
                .append(MoneyHelper.FormatDabloonWord(reward.baseReward()).withStyle(ChatFormatting.GREEN))
                .append(multiplierLine("Sunday bonus", reward.isSunday()))
                .append(multiplierLine("Member bonus", reward.isMember()))
                .append(Component.literal("\nTotal: ").withStyle(ChatFormatting.GRAY))
                .append(MoneyHelper.FormatDabloonWord(reward.totalReward()).withStyle(ChatFormatting.GREEN));
    }

    private static Component multiplierLine(String label, boolean applied) {
        if (applied) {
            return Component.literal("\n" + label + ": ×1.2").withStyle(ChatFormatting.GREEN);
        }
        return Component.literal("\n" + label + ": ×1.2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
    }

    public record RewardCalculation(
            int baseReward,
            boolean isSunday,
            boolean isMember,
            int totalReward
    ) {
    }
}
