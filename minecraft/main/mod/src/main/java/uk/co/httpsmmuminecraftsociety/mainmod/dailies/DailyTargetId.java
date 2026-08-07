package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class DailyTargetId {
    private DailyTargetId() {
    }

    public static String of(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String of(EntityType<?> entityType) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
    }

    public static String of(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public static String of(Holder<?> holder) {
        return holder.unwrapKey().orElseThrow().identifier().toString();
    }

    public static String of(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    public static String of(TagKey<?> tag) {
        return "#" + tag.location();
    }
}
