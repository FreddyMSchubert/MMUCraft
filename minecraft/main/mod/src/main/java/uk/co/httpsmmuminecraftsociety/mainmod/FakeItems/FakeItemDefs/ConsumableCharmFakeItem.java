package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;

public class ConsumableCharmFakeItem extends CharmFakeItem
{
    private final float consumeSeconds;
    private final boolean isDrink;

    public ConsumableCharmFakeItem(int effectId, String title, float consumeSeconds, boolean isDrink, Rarity rarity, Charm charm, String... tooltip)
    {
        super(effectId, title, rarity, charm, tooltip);

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
}
