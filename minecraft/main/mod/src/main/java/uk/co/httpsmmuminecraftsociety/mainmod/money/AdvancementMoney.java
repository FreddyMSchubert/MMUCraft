package uk.co.httpsmmuminecraftsociety.mainmod.money;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;

public final class AdvancementMoney {
    private AdvancementMoney() {}

    public static int moneyForExperience(int experience) {
        double raw = ((double) experience * (double) experience / 600.0D) + 5.0D;
        return (int) (Math.ceil(raw / 5.0D) * 5);
    }

    public static Component appendMoneyReward(DisplayInfo displayInfo, int experience) {
        int money = moneyForExperience(experience);
        return displayInfo.getDescription()
                .copy()
                .append(Component.literal("\nReward: " + money + " dabloons").withStyle(ChatFormatting.GOLD));
    }
}
