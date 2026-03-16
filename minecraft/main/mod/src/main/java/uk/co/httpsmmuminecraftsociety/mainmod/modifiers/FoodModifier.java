package uk.co.httpsmmuminecraftsociety.mainmod.modifiers;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.IdentityHashMap;
import java.util.Map;

public class FoodModifier
{
    public static void onDefaultItemComponentsModify(DefaultItemComponentEvents.ModifyContext context)
    {
        TUNES.forEach((item, tune) -> context.modify(item, builder -> apply(builder, item, tune)));
    }

    private record FoodTuning(
            float hungerBars,
            float saturationBars,
            int directHearts,
            int stackSize
    ) {}

    private static final Map<Item, FoodTuning> TUNES = new IdentityHashMap<>();

    static {
        // trash / emergency garbage
        put(Items.ROTTEN_FLESH,      1.0F, 1.5F, 0, 64);
        put(Items.SPIDER_EYE,        1.0F, 1.5F, 0, 64);
        put(Items.POISONOUS_POTATO,  1.0F, 1.0F, 0, 64);
        put(Items.PUFFERFISH,        1.0F, 0.5F, 0, 64);

        // ultra-common / easy farm junk
        put(Items.DRIED_KELP,        0.5F, 0.5F, 0, 64);
        put(Items.BEETROOT,          0.5F, 0.5F, 0, 64);
        put(Items.MELON_SLICE,       1.0F, 1.0F, 0, 64);
        put(Items.SWEET_BERRIES,     1.0F, 1.0F, 0, 64);
        put(Items.GLOW_BERRIES,      1.5F, 1.5F, 0, 64);
        put(Items.POTATO,            1.0F, 1.0F, 0, 64);
        put(Items.APPLE,             1.5F, 1.5F, 0, 64);
        put(Items.CARROT,            1.5F, 1.5F, 0, 64);
        put(Items.BREAD,             2.0F, 2.0F, 0, 64);

        // raw meats / fish
        put(Items.COD,               1.5F, 1.5F, 0, 64);
        put(Items.SALMON,            1.5F, 2.0F, 0, 64);
        put(Items.TROPICAL_FISH,     0.5F, 0.5F, 0, 64);
        put(Items.CHICKEN,           1.5F, 1.5F, 0, 64);
        put(Items.BEEF,              1.5F, 1.5F, 0, 64);
        put(Items.PORKCHOP,          1.5F, 1.5F, 0, 64);
        put(Items.MUTTON,            1.5F, 1.5F, 0, 64);
        put(Items.RABBIT,            1.5F, 1.5F, 0, 64);

        // cooked singles: solid staples, not premium
        put(Items.BAKED_POTATO,      2.5F, 2.0F, 0, 64);
        put(Items.COOKED_COD,        2.5F, 2.0F, 0, 64);
        put(Items.COOKED_CHICKEN,    3.0F, 2.5F, 0, 64);
        put(Items.COOKED_RABBIT,     3.5F, 3.0F, 0, 64);
        put(Items.COOKED_SALMON,     2.5F, 3.0F, 0, 64);
        put(Items.COOKED_MUTTON,     3.0F, 3.0F, 0, 64);
        put(Items.COOKED_BEEF,       3.0F, 3.5F, 0, 64);
        put(Items.COOKED_PORKCHOP,   3.0F, 3.5F, 0, 64);

        // processed / special but still stackable
        put(Items.PUMPKIN_PIE,       4.0F, 4.0F, 0, 32);
        put(Items.CHORUS_FRUIT,      1.5F, 1.5F, 0, 64);
        put(Items.HONEY_BOTTLE,      1.0F, 1.5F, 2, 16);
        put(Items.COOKIE,            1.5F, 1.5F, 2, 64);

        // gold foods
        put(Items.GOLDEN_CARROT,          3.5F, 4.5F, 0, 32);
        put(Items.GOLDEN_APPLE,           4.0F, 6.0F, 0, 1);
        put(Items.ENCHANTED_GOLDEN_APPLE, 4.0F, 7.0F, 4, 1);

        // bowls / soups / premium foods
        put(Items.BEETROOT_SOUP,     7.5F, 4.5F, 4, 1);
        put(Items.MUSHROOM_STEW,     4.0F, 8.0F, 4, 1);
        put(Items.SUSPICIOUS_STEW,   3.0F, 4.0F, 2, 1);
        put(Items.RABBIT_STEW,       5.5F, 6.0F, 2, 1);
    }

    private static void apply(DataComponentMap.Builder builder, Item item, FoodTuning tune) {
        ItemStack vanillaStack = item.getDefaultInstance();

        FoodProperties vanillaFood = vanillaStack.get(DataComponents.FOOD);
        if (vanillaFood == null) {
            return;
        }

        FoodProperties.Builder foodBuilder = new FoodProperties.Builder()
                .nutrition(hungerBarsToNutrition(tune.hungerBars))
                .saturationModifier(satBarsToSaturationModifier(tune.saturationBars));

        if (vanillaFood.canAlwaysEat()) {
            foodBuilder.alwaysEdible();
        }

        builder.set(DataComponents.FOOD, foodBuilder.build());
        builder.set(DataComponents.MAX_STACK_SIZE, tune.stackSize);

        Consumable.Builder consumableBuilder = copyVanillaConsumable(vanillaStack.get(DataComponents.CONSUMABLE));

        ApplyStatusEffectsConsumeEffect directHealEffect = createInstantHealthEffect(tune.directHearts);
        if (directHealEffect != null) {
            consumableBuilder.onConsume(directHealEffect);
        }

        builder.set(DataComponents.CONSUMABLE, consumableBuilder.build());
    }

    private static Consumable.Builder copyVanillaConsumable(Consumable vanillaConsumable) {
        Consumable.Builder builder = Consumable.builder();

        if (vanillaConsumable == null) {
            return builder;
        }

        builder.consumeSeconds(vanillaConsumable.consumeSeconds())
                .animation(vanillaConsumable.animation())
                .sound(vanillaConsumable.sound())
                .hasConsumeParticles(vanillaConsumable.hasConsumeParticles());

        for (ConsumeEffect effect : vanillaConsumable.onConsumeEffects()) {
            builder.onConsume(effect);
        }

        return builder;
    }

    public static int hungerBarsToNutrition(float hungerBars) {
        return Math.max(1, Math.round(hungerBars * 2.0F));
    }
    public static float satBarsToSaturationModifier(float saturationBars) {
        return saturationBars / 10.0F;
    }

    public static ApplyStatusEffectsConsumeEffect createInstantHealthEffect(int directHearts) {
        if (directHearts <= 0) {
            return null;
        }
        if (directHearts < 2 || (directHearts & (directHearts - 1)) != 0) {
            MainMod.LOGGER.warn(
                    "directHearts={} cannot be represented exactly by INSTANT_HEALTH. Valid exact values are powers of 2 (2, 4, 8, 16, ...) - Not applying healing.",
                    directHearts
            );
            return null;
        }

        int amplifier = Integer.numberOfTrailingZeros(directHearts) - 1;
        return new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, amplifier), 1.0F);
    }

    private static void put(Item item, float hungerBars, float saturationBars, int directHearts, int stackSize) {
        TUNES.put(item, new FoodTuning(hungerBars, saturationBars, directHearts, stackSize));
    }
}
