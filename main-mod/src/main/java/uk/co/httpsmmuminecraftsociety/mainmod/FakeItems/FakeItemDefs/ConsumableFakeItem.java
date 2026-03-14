package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.FoodModifier;

import java.util.ArrayList;
import java.util.List;

public class ConsumableFakeItem extends FakeItem
{
    private static final String CONSUMABLE_ID_NBT = "consumable_id";

    private final int consumableId;
    private final boolean isDrink;
    private final float consumeSeconds;
    private final boolean canAlwaysEat;
    private final float hungerBars;
    private final float saturationBars;
    private final int directHearts;
    private final List<MobEffectInstance> effects;
    private final ItemStack useRemainder;

    public ConsumableFakeItem(
            int consumableId,
            String model_id,
            String title,
            boolean isDrink,
            float consumeSeconds,
            boolean canAlwaysEat,
            float hungerBars,
            float saturationBars,
            int directHearts,
            List<MobEffectInstance> effects,
            ItemStack useRemainder,
            int maxStackSize,
            String... tooltip
    ) {
        super(Items.COMMAND_BLOCK, model_id, title, Rarity.COMMON, maxStackSize, tooltip);

        this.consumableId = consumableId;
        this.isDrink = isDrink;
        this.consumeSeconds = consumeSeconds;
        this.canAlwaysEat = canAlwaysEat;
        this.hungerBars = hungerBars;
        this.saturationBars = saturationBars;
        this.directHearts = directHearts;
        this.effects = effects;
        this.useRemainder = useRemainder;
    }

    public int getConsumableId() {
        return consumableId;
    }
    public boolean isDrink() {
        return isDrink;
    }
    public float getConsumeSeconds() {
        return consumeSeconds;
    }
    public boolean isCanAlwaysEat() {
        return canAlwaysEat;
    }
    public float getHungerBars() {
        return hungerBars;
    }
    public float getSaturationBars() {
        return saturationBars;
    }
    public int getDirectHearts() {
        return directHearts;
    }
    public List<MobEffectInstance> getEffects() {
        return effects;
    }
    public ItemStack getUseRemainder() {
        return useRemainder;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();

        // consumable id
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putInt(CONSUMABLE_ID_NBT, this.consumableId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

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
        stack.set(DataComponents.USE_REMAINDER, new UseRemainder(useRemainder));

        return stack;
    }
}
