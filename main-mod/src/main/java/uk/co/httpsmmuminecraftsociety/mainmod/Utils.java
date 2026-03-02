package uk.co.httpsmmuminecraftsociety.mainmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.UUID;

public class Utils
{
    public static final String TAG_TICK = "charm-ontickcallback";

    public static void applyModifier(ServerPlayer player,
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

    public static void removeModifier(ServerPlayer player,
                                       net.minecraft.core.Holder<Attribute> attr,
                                       Identifier id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        AttributeModifier existing = inst.getModifier(id);
        if (existing != null) inst.removeModifier(existing);
    }
}
