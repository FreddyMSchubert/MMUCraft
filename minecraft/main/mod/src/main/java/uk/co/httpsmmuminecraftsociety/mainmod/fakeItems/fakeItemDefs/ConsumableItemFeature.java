package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FoodModifier;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ConsumableItemFeature(
        boolean isDrink,
        float consumeSeconds,
        boolean canAlwaysEat,
        float hungerBars,
        float saturationBars,
        int directHearts,
        List<MobEffectInstance> effects,
        Optional<ItemStackTemplate> useRemainder
) implements ItemFeature
{
    public static ConsumableItemFeature of(JsonObject json)
    {
        boolean isDrink = json.get("isDrink").getAsBoolean();
        float consumeSeconds = json.get("consumeSeconds").getAsFloat();
        boolean canAlwaysEat = json.get("canAlwaysEat").getAsBoolean();
        float hungerBars = json.get("hungerBars").getAsFloat();
        float saturationBars = json.get("saturationBars").getAsFloat();
        int directHearts = json.get("directHearts").getAsInt();

        List<MobEffectInstance> effects = new ArrayList<>();
        for (JsonElement element : json.get("effects").getAsJsonArray())
            effects.add(JsonUtils.parseMobEffect(element.getAsJsonObject()));

        Optional<Item> useRemainderItem = JsonUtils.resolveItem(json.get("useRemainderItem").getAsString());
        Optional<ItemStackTemplate> useRemainder = useRemainderItem.map(ItemStackTemplate::new);

        return new ConsumableItemFeature(isDrink, consumeSeconds, canAlwaysEat, hungerBars, saturationBars, directHearts, effects, useRemainder);
    }

    @Override
    public void apply(ItemStack stack)
    {
        // consumable component
        Consumable.Builder consumableBuilder = Consumable.builder()
                .consumeSeconds(consumeSeconds)
                .animation(isDrink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                .sound(isDrink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT)
                .hasConsumeParticles(false);
        List<MobEffectInstance> consumeEffects = new ArrayList<>();
        if (effects != null && !effects.isEmpty()) {
            consumeEffects.addAll(effects);
        }
        ApplyStatusEffectsConsumeEffect directHealEffect = FoodModifier.createInstantHealthEffect(this.directHearts);
        if (directHealEffect != null) {
            consumableBuilder.onConsume(directHealEffect);
        }
        if (!consumeEffects.isEmpty()) {
            consumableBuilder.onConsume(new ApplyStatusEffectsConsumeEffect(consumeEffects, 1.0F));
        }
        stack.set(DataComponents.CONSUMABLE, consumableBuilder.build());

        // food component
        FoodProperties.Builder fpb = new FoodProperties.Builder()
                .nutrition(FoodModifier.hungerBarsToNutrition(this.hungerBars))
                .saturationModifier(FoodModifier.satBarsToSaturationModifier(this.saturationBars));
        if (canAlwaysEat) {
            fpb.alwaysEdible();
        }
        stack.set(DataComponents.FOOD, fpb.build());

        // use remainder component
        if (useRemainder.isPresent())
            stack.set(DataComponents.USE_REMAINDER, new UseRemainder(useRemainder.get()));
    }

    @Override
    public void validate()
    {

    }
}
