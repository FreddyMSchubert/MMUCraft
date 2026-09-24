package dev.freddy.killshift;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

final class ShapeEffects {
    private ShapeEffects() { }

    private static Identifier effect(EntityType<?> type) {
        if (type == EntityTypes.CREEPER) return Identifier.withDefaultNamespace("creeper");
        if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) {
            return Identifier.withDefaultNamespace("spider");
        }
        if (type == EntityTypes.ENDERMAN || type == EntityTypes.ENDERMITE || type == EntityTypes.SHULKER) {
            return Identifier.withDefaultNamespace("invert");
        }
        return null;
    }

    static void apply(ServerPlayer player, ShapeState state) {
        Identifier id = effect(state.form.type());
        if (id != null && !player.getPostEffects().contains(id)) {
            state.ownsPostEffect = player.addPostEffect(id);
        }
    }

    static void clear(ServerPlayer player, ShapeState state) {
        Identifier id = effect(state.form.type());
        if (id != null && state.ownsPostEffect) {
            player.removePostEffect(id);
            state.ownsPostEffect = false;
        }
    }
}
