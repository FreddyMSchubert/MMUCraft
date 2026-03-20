package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public class Utils
{
    public static void applyPlayerModifier(ServerPlayer player,
                                           net.minecraft.core.Holder<Attribute> attr,
                                           Identifier id,
                                           double amount,
                                           AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        AttributeModifier existing = inst.getModifier(id);
        if (existing != null) inst.removeModifier(existing);

        inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    public static void removePlayerModifier(ServerPlayer player,
                                            net.minecraft.core.Holder<Attribute> attr,
                                            Identifier id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        AttributeModifier existing = inst.getModifier(id);
        if (existing != null) inst.removeModifier(existing);
    }

    public static ItemStack applyItemAttrModifier(ItemStack stack, String id, Holder<Attribute> attributeType, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slotgroup) {
        Identifier attrId = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, id);

        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        AttributeModifier newMod = new AttributeModifier(attrId, amount, operation);
        ItemAttributeModifiers newMods = mods.withModifierAdded(attributeType, newMod, slotgroup);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, newMods);

        return stack;
    }

    public static ItemStack removeItemAttrModifier(ItemStack stack, String id, Holder<Attribute> attributeType) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Identifier toRemoveId = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, id);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry mod : mods.modifiers()) {
            if (mod.matches(attributeType, toRemoveId)) continue; // skip the attr to remove, then reassemble
            builder.add(mod.attribute(), mod.modifier(), mod.slot());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        return stack;
    }

    public static int rgbToMinecraftColor(int r, int g, int b) {
        return ARGB.color(0, r, g, b);
    }
}
