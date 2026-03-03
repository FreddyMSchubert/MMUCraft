package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FakeItem
{
    private final Item baseItem;
    private final String model_id;
    private final Component title;
    private final List<Component> tooltip;
    private final Rarity rarity;
    private final int maxStackSize;

    public FakeItem(Item baseItem, String model_id, String title, Rarity rarity, int maxStackSize, String... tooltip)
    {
        this.baseItem = baseItem;
        this.model_id = model_id;
        this.title = Component.literal(title);
        this.tooltip = new ArrayList<>();
        for (String loreLine : tooltip)
            if (loreLine != null && !loreLine.isEmpty())
                this.tooltip.add(Component.literal(loreLine));
        this.rarity = rarity;
        this.maxStackSize = maxStackSize;
    }

    public Item getBaseItem() {
        return baseItem;
    }
    public String getModelId() {
        return model_id;
    }
    public Component getTitle() {
        return title;
    }
    public List<Component> getTooltip() {
        return tooltip;
    }
    public Rarity getRarity() {
        return rarity;
    }
    public int getMaxStackSize() {
        return maxStackSize;
    }

    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem, 1);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(model_id), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, title);
        if (tooltip != null && !tooltip.isEmpty() && !tooltip.stream().allMatch(Objects::isNull))
            stack.set(DataComponents.LORE, new ItemLore(tooltip));
        stack.set(DataComponents.RARITY, rarity);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);

        return stack;
    }
}
