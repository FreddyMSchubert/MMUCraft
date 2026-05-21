package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishRarity;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishShapes;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

import java.util.ArrayList;
import java.util.List;

public record FishItemFeature(
        FishingPersonality personality
) implements ItemFeature {
    @Override
    public void apply(ItemStack stack) {
        List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.add(Component.literal("Rarity: " + personality.rarity().displayName()).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    @Override
    public void validate() {
    }

    public static FishItemFeature of(JsonObject rootJson, JsonObject json) {
        FishRarity rarity = FishRarity.fromJsonValue(rootJson.get("rarity").getAsString());
        float secondsAwayFromBobber = json.get("secondsAwayFromBobber").getAsFloat();
        float approachSeconds = json.get("approachSeconds").getAsFloat();
        float retreatSeconds = json.get("retreatSeconds").getAsFloat();
        float averageCatchSeconds = json.get("averageCatchSeconds").getAsFloat();
        float averageBounces = calculateAverageBounces(averageCatchSeconds, approachSeconds, retreatSeconds, secondsAwayFromBobber);
        return new FishItemFeature(new FishingPersonality(
                rarity,
                json.get("struggleSeconds").getAsFloat(),
                FishShapes.fromJsonValue(json.get("shape").getAsString()).value(),
                json.get("size").getAsFloat(),
                secondsAwayFromBobber,
                approachSeconds,
                retreatSeconds,
                json.get("retreatDistance").getAsFloat(),
                averageBounces
        ));
    }

    private static float calculateAverageBounces(
            float averageCatchSeconds,
            float approachSeconds,
            float retreatSeconds,
            float secondsAwayFromBobber
    ) {
        float repeatSeconds = retreatSeconds + secondsAwayFromBobber;
        float bounceSeconds = approachSeconds + repeatSeconds;
        return Math.max(1.0F, (averageCatchSeconds + repeatSeconds) / bounceSeconds);
    }
}
