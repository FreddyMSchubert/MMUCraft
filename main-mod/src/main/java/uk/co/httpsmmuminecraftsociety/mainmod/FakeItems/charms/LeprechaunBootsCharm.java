package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class LeprechaunBootsCharm implements Charm, EquippedTickCallbackCharm
{
    public static final Identifier SIZE_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_size");
    public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_speed");
    public static final Identifier BLOCK_INTERACTION_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_interaction_block");
    public static final Identifier ENTITY_INTERACTION_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_interaction_entity");
    public static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_step");
    public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_health");
    public static final Identifier LUCK_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_luck");
    public static final Identifier JUMP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_jump");
    public static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "giants_boots_attack");

    @Override
    public String id()
    {
        return "cosmetic-charm-leprechaun-boots";
    }

    @Override
    public @NotNull ItemStack onCreation(ItemStack stack)
    {
        AttributeModifier scale_mod = new AttributeModifier(
                SIZE_ID,
                -0.5,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier speed_mod = new AttributeModifier(
                SPEED_ID,
                0.1,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier block_interaction_mod = new AttributeModifier(
                BLOCK_INTERACTION_ID,
                -4,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier entity_interaction_mod = new AttributeModifier(
                ENTITY_INTERACTION_ID,
                -2.5,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier step_mod = new AttributeModifier(
                STEP_ID,
                -0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        AttributeModifier jump_mod = new AttributeModifier(
                JUMP_ID,
                -0.2,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier health_mod = new AttributeModifier(
                HEALTH_ID,
                -19,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier attack_mod = new AttributeModifier(
                ATTACK_ID,
                -1,
                AttributeModifier.Operation.ADD_VALUE
        );
        AttributeModifier luck_mod = new AttributeModifier(
                LUCK_ID,
                1,
                AttributeModifier.Operation.ADD_VALUE
        );

        ItemAttributeModifiers attrs = ItemAttributeModifiers.builder()
                .add(Attributes.SCALE, scale_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.MOVEMENT_SPEED, speed_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.BLOCK_INTERACTION_RANGE, block_interaction_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.ENTITY_INTERACTION_RANGE, entity_interaction_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.MAX_HEALTH, health_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.STEP_HEIGHT, step_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.LUCK, luck_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.JUMP_STRENGTH, jump_mod, EquipmentSlotGroup.FEET)
                .add(Attributes.ATTACK_DAMAGE, attack_mod, EquipmentSlotGroup.FEET)
                .build();

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);
        return stack;
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        if (level.getGameTime() % 15 != 0) return stack;

        MobEffectInstance inst = new MobEffectInstance(
                MobEffects.WEAKNESS,
                220,
                0,
                false,
                false,
                false
        );
        player.addEffect(inst);
        return stack;
    }
}
