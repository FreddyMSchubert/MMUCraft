package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class HikingBootsCharm implements Charm
{
    public static final String HIKING_BOOTS_CHARM_ID_BEGINNING = "cosmetic-charm-hiking-boots-";
    private static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "hiking_boots_step_id");

    private final int level;

    public HikingBootsCharm(int level) {
        this.level = level;
    }

    @Override
    public String id()
    {
        return HIKING_BOOTS_CHARM_ID_BEGINNING + level;
    }

    @Override
    public @NotNull ItemStack onCreation(ItemStack stack)
    {
        AttributeModifier mod = new AttributeModifier(
                STEP_ID,
                getStepHeightForLevel(this.level),
                AttributeModifier.Operation.ADD_VALUE
        );

        ItemAttributeModifiers attrs = ItemAttributeModifiers.builder()
                .add(Attributes.STEP_HEIGHT, mod, EquipmentSlotGroup.FEET)
                .build();

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);
        return stack;
    }

    public static float getStepHeightForLevel(int level) {
        return switch (level)
        {
            case 0 -> 0.5f;
            case 1 -> 1f;
            case 2 -> 1.5f;
            default -> 105f;
        };
    }
}
