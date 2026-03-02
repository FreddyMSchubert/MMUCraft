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
    @Override
    public String id()
    {
        return "cosmetic-charm-open-heart";
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
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        return null;
    }
}
