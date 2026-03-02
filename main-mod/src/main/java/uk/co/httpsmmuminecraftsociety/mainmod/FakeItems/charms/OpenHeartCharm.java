package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class OpenHeartCharm implements Charm
{
    public static final String OPEN_HEART_CHARM_ID = "cosmetic-charm-open-heart";

    @Override
    public String id()
    {
        return OPEN_HEART_CHARM_ID;
    }

    @Override
    public ItemStack onCreation(ItemStack stack)
    {
        AttributeModifier mod = new AttributeModifier(
                Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "open_heart_charm_health_boost"),
                6.0,
                AttributeModifier.Operation.ADD_VALUE
        );
        ItemAttributeModifiers attrs = ItemAttributeModifiers.builder()
                .add(Attributes.MAX_HEALTH, mod, EquipmentSlotGroup.CHEST)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);

        return stack;
    }

    @Override
    public boolean subcribeToOnTick()
    {
        return false;
    }

    @Override
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        return null;
    }
}
