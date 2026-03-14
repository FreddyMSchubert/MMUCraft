package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

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
            float saturationModifier,
            float directHearts,
            int stackSize
    ) {}

    private static final Map<Item, FoodTuning> TUNES = new IdentityHashMap<>();

    static {
        // trash / emergency garbage
        put(Items.ROTTEN_FLESH,      1.0F, 0.15F, 0.0F, 64);
        put(Items.SPIDER_EYE,        1.0F, 0.15F, 0.0F, 64);
        put(Items.POISONOUS_POTATO,  1.0F, 0.10F, 0.0F, 64);
        put(Items.PUFFERFISH,        1.0F, 0.05F, 0.0F, 64);

        // ultra-common / easy farm junk
        put(Items.DRIED_KELP,        0.5F, 0.05F, 0.0F, 64);
        put(Items.BEETROOT,          0.5F, 0.05F, 0.0F, 64);
        put(Items.COOKIE,            0.5F, 0.05F, 0.0F, 64);
        put(Items.MELON_SLICE,       1.0F, 0.10F, 0.0F, 64);
        put(Items.SWEET_BERRIES,     1.0F, 0.10F, 0.0F, 64);
        put(Items.GLOW_BERRIES,      1.5F, 0.15F, 0.0F, 64);
        put(Items.POTATO,            1.0F, 0.10F, 0.0F, 64);
        put(Items.APPLE,             1.5F, 0.15F, 0.0F, 64);
        put(Items.CARROT,            1.5F, 0.15F, 0.0F, 64);
        put(Items.BREAD,             2.0F, 0.20F, 0.0F, 64);

        // raw meats / fish
        put(Items.COD,               1.5F, 0.15F, 0.0F, 64);
        put(Items.SALMON,            1.5F, 0.20F, 0.0F, 64);
        put(Items.TROPICAL_FISH,     0.5F, 0.05F, 0.0F, 64);
        put(Items.CHICKEN,           1.5F, 0.15F, 0.0F, 64);
        put(Items.BEEF,              1.5F, 0.15F, 0.0F, 64);
        put(Items.PORKCHOP,          1.5F, 0.15F, 0.0F, 64);
        put(Items.MUTTON,            1.5F, 0.15F, 0.0F, 64);
        put(Items.RABBIT,            1.5F, 0.15F, 0.0F, 64);

        // cooked singles: solid staples, not premium
        put(Items.BAKED_POTATO,      2.5F, 0.20F, 0.0F, 64);
        put(Items.COOKED_COD,        2.5F, 0.20F, 0.0F, 64);
        put(Items.COOKED_CHICKEN,    3.0F, 0.25F, 0.0F, 64);
        put(Items.COOKED_RABBIT,     3.0F, 0.30F, 0.0F, 64);
        put(Items.COOKED_SALMON,     3.0F, 0.30F, 0.0F, 64);
        put(Items.COOKED_MUTTON,     3.5F, 0.30F, 0.0F, 64);
        put(Items.COOKED_BEEF,       4.0F, 0.35F, 0.0F, 64);
        put(Items.COOKED_PORKCHOP,   4.0F, 0.35F, 0.0F, 64);

        // processed / special but still stackable
        put(Items.PUMPKIN_PIE,       4.0F, 0.50F, 0.0F, 32);
        put(Items.CHORUS_FRUIT,      3.5F, 0.45F, 0.0F, 64);
        put(Items.HONEY_BOTTLE,      3.0F, 0.40F, 1.0F, 16);

        // gold foods
        put(Items.GOLDEN_CARROT,         3.5F, 0.65F, 0.0F, 64);
        put(Items.GOLDEN_APPLE,          4.0F, 1.00F, 3.0F, 1);
        put(Items.ENCHANTED_GOLDEN_APPLE,5.0F, 1.20F, 6.0F, 1);

        // bowls / soups / premium foods
        put(Items.BEETROOT_SOUP,     5.5F, 1.05F, 5.5F, 1);
        put(Items.MUSHROOM_STEW,     5.5F, 0.80F, 10F, 1);
        put(Items.SUSPICIOUS_STEW,   5.0F, 0.70F, 3.0F, 1);
        put(Items.RABBIT_STEW,       5.0F, 0.75F, 5.0F, 1);
    }

    private static void apply(DataComponentMap.Builder builder, Item item, FoodTuning tune) {
        FoodProperties vanillaFood = item.components().get(DataComponents.FOOD);
        if (vanillaFood == null) {
            return;
        }

        FoodProperties.Builder foodBuilder = new FoodProperties.Builder()
                .nutrition(toNutrition(tune.hungerBars()))
                .saturationModifier(tune.saturationModifier());

        if (vanillaFood.canAlwaysEat()) {
            foodBuilder.alwaysEdible();
        }

        builder.set(DataComponents.FOOD, foodBuilder.build());
        builder.set(DataComponents.MAX_STACK_SIZE, tune.stackSize());
    }

    private static int toNutrition(float hungerBars) {
        return Math.max(1, Math.round(hungerBars * 2.0F));
    }

    public static float directHearts(Item item) {
        FoodTuning tuning = TUNES.get(item);
        return tuning == null ? 0.0F : tuning.directHearts();
    }

    private static void put(Item item, float hungerBars, float saturationModifier, float directHearts, int stackSize) {
        TUNES.put(item, new FoodTuning(hungerBars, saturationModifier, directHearts, stackSize));
    }
}
