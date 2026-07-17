package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishFurnaceResult;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishRarity;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishSize;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishSpawnTag;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishShapes;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record FishItemFeature(
        FishingPersonality personality,
        FishSize length,
        FishFurnaceResult furnaceResult,
        Set<FishSpawnTag> spawnTags
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
        JsonObject size = json.getAsJsonObject("size");
        JsonObject catching = json.has("catching") ? json.getAsJsonObject("catching") : new JsonObject();
        FishRarity rarity = FishRarity.fromJsonValue(rootJson.get("rarity").getAsString());
        // The item id is the seed: reloads and separate catches keep this species consistent.
        RandomSource random = RandomSource.create(rootJson.get("id").getAsString().hashCode());
        int rarityLevel = rarity.ordinal();

        // Roll every default first, so one JSON override cannot change the other generated values.
        float shadowScale = override(catching, "shadowScale", between(random, 0.5F, 1.5F));
        float secondsAwayFromBobber = override(catching, "secondsAwayFromBobber", between(random, 0.1F, 0.5F));
        float approachSeconds = override(catching, "approachSeconds", between(random, 0.15F, 0.5F));
        float retreatSeconds = override(catching, "retreatSeconds", between(random, 0.2F, 1.5F));
        float retreatDistance = override(catching, "retreatDistance", between(random, 0.5F, 1.75F));
        float averageCatchSeconds = override(catching, "averageCatchSeconds",
                between(random, 0.0F, 15.0F + 3.0F * rarityLevel));
        float struggleSeconds = override(catching, "struggleSeconds",
                between(random, 1.0F + 0.5F * rarityLevel, 2.5F + 1.5F * rarityLevel));
        float averageBounces = calculateAverageBounces(averageCatchSeconds, approachSeconds, retreatSeconds, secondsAwayFromBobber);
        double averageLengthCm = size.get("averageLengthCm").getAsDouble();
        EnumSet<FishSpawnTag> spawnTags = EnumSet.noneOf(FishSpawnTag.class);
        for (JsonElement tag : json.getAsJsonArray("tags")) {
            spawnTags.add(FishSpawnTag.fromJsonValue(tag.getAsString()));
        }
        return new FishItemFeature(
                new FishingPersonality(
                        rarity,
                        struggleSeconds,
                        FishShapes.fromJsonValue(json.get("shape").getAsString()).value(),
                        shadowScale,
                        secondsAwayFromBobber,
                        approachSeconds,
                        retreatSeconds,
                        retreatDistance,
                        averageBounces
                ),
                new FishSize(
                        averageLengthCm,
                        size.has("lengthDeviationCm") ? size.get("lengthDeviationCm").getAsDouble() : averageLengthCm / 3.0
                ),
                FishFurnaceResult.fromJsonValue(json.get("furnaceResult").getAsString()),
                Set.copyOf(spawnTags)
        );
    }

    private static float between(RandomSource random, float minimum, float maximum) {
        return minimum + random.nextFloat() * (maximum - minimum);
    }

    private static float override(JsonObject json, String name, float generatedValue) {
        return json.has(name) ? json.get(name).getAsFloat() : generatedValue;
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
