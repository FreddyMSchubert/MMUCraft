package dev.freddy.killshift;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;

record MobForm(
        EntityType<?> type,
        MobTraits traits,
        Map<Holder<Attribute>, Double> attributes,
        double scale
) {
    MobForm {
        attributes = Map.copyOf(attributes);
    }

    CompoundTag save() {
        CompoundTag result = new CompoundTag();
        result.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        result.putDouble("scale", scale);
        result.putInt("movementVersion", 1);
        CompoundTag values = new CompoundTag();
        attributes.forEach((attribute, value) ->
                values.putDouble(BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()).toString(), value));
        result.put("attributes", values);
        return result;
    }

    static MobForm load(CompoundTag data) {
        Identifier id = Identifier.tryParse(data.getStringOr("type", ""));
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || (!MobRegistry.supports(type) && type != EntityTypes.PLAYER)) return null;
        CompoundTag values = data.getCompoundOrEmpty("attributes");
        Map<Holder<Attribute>, Double> attributes = new HashMap<>();
        for (Holder<Attribute> attribute : MobRegistry.COPIED_ATTRIBUTES) {
            String key = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()).toString();
            values.getDouble(key).ifPresent(value -> attributes.put(attribute, value));
        }
        if (type != EntityTypes.PLAYER && data.getIntOr("movementVersion", 0) == 0) {
            attributes.computeIfPresent(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    (attribute, speed) -> speed * MobRegistry.PLAYER_MOVEMENT_FACTOR);
        }
        return new MobForm(type, MobRegistry.traits(type), attributes,
                Math.clamp(data.getDoubleOr("scale", 1.0), 0.0625, 16.0));
    }
}
