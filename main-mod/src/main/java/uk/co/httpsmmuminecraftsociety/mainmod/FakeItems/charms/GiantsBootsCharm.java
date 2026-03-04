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

public class GiantsBootsCharm implements Charm
{
    public static final Identifier SIZE_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_size");
    public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_speed");
    public static final Identifier INTERACTION_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_interaction");
    public static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_step");
    public static final Identifier JUMP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_jump");
    public static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_attack");

    @Override
    public String id()
    {
        return "cosmetic-charm-giants-boots";
    }

    @Override
    public @NotNull ItemStack onCreation(ItemStack stack)
    {
        AttributeModifier scale_mod = new AttributeModifier(
                SIZE_ID,
                2,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier speed_mod = new AttributeModifier(
                SPEED_ID,
                -0.05,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier interaction_mod = new AttributeModifier(
                INTERACTION_ID,
                2,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier step_mod = new AttributeModifier(
                STEP_ID,
                2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        AttributeModifier jump_mod = new AttributeModifier(
                JUMP_ID,
                0.2,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier attack_mod = new AttributeModifier(
                ATTACK_ID,
                -0.9,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );

        ItemAttributeModifiers attrs = ItemAttributeModifiers.builder()
                .add(Attributes.SCALE, scale_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.MOVEMENT_SPEED, speed_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.BLOCK_INTERACTION_RANGE, interaction_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.ENTITY_INTERACTION_RANGE, interaction_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.STEP_HEIGHT, step_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.JUMP_STRENGTH, jump_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.ATTACK_SPEED, attack_mod, EquipmentSlotGroup.FEET)
                .build();

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);
        return stack;
    }
}
