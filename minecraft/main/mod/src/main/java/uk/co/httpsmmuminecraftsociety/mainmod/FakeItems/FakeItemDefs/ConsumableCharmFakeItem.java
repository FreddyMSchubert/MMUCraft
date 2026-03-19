package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.itemdata.CharmCodeRegistry;

public class ConsumableCharmFakeItem extends CharmFakeItem
{
    private final float consumeSeconds;
    private final boolean isDrink;

    public ConsumableCharmFakeItem(int effectId, String model_id, String title, float consumeSeconds, boolean isDrink, Rarity rarity, Charm charm, String... tooltip)
    {
        super(effectId, model_id, title, rarity, charm, tooltip);

        this.consumeSeconds = consumeSeconds;
        this.isDrink = isDrink;
    }

    public float getConsumeSeconds() {
        return consumeSeconds;
    }
    public boolean isDrink() {
        return isDrink;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();
        stack.set(DataComponents.CONSUMABLE, Consumable.builder()
                        .consumeSeconds(consumeSeconds)
                        .animation(isDrink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                        .sound(isDrink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT)
                        .hasConsumeParticles(false)
                .build());
        return stack;
    }

    public static ConsumableCharmFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 1);
        JsonObject behaviour = getBehaviourObject(root);
        JsonObject consumable = getConsumableObject(root);

        int effectId = requiredInt(behaviour, root, "effectId", sourcePath);
        Charm charm = CharmCodeRegistry.getRequired(effectId, sourcePath);

        boolean isDrink = optionalBoolean(consumable, behaviour, "isDrink", false);
        float consumeSeconds = optionalFloat(consumable, behaviour, "consumeSeconds", 1.6f);

        return new ConsumableCharmFakeItem(
                effectId,
                common.modelId(),
                common.title(),
                consumeSeconds,
                isDrink,
                common.rarity(),
                charm,
                common.tooltip()
        );
    }
}
