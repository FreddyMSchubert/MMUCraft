package uk.co.httpsmmuminecraftsociety.mainmod.money;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;

public final class AdvancementMoney {
    private AdvancementMoney() {}

    public static int moneyForExperience(String advancementTitle, int experience) {
        if ("Adventuring Time".equals(advancementTitle)) {
            return 67;
        }

        int money = (int) Math.ceil(((double) experience * (double) experience / 600.0D) + 5.0D);
        if (experience == 0) money = 3;
        return money;
    }

    public static Component appendMoneyReward(DisplayInfo displayInfo, int experience) {
        int money = moneyForExperience(displayInfo.getTitle().getString(), experience);
        return displayInfo.getDescription()
                .copy()
                .append(Component.literal("\nReward: " + money + " dabloons").withStyle(ChatFormatting.GOLD));
    }
}
