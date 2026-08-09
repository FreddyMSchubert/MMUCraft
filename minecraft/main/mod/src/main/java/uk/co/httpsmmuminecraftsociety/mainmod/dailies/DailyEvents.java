package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class DailyEvents {
    private DailyEvents() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                DailyTaskManager.record(serverPlayer, DailyTaskEvent.of(
                        DailyTaskEvent.Type.BREAK_BLOCK,
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                ));
            }
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register(DailyEvents::onDamage);

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && level.getBlockState(hit.getBlockPos()).is(Blocks.FLETCHING_TABLE)) {
                DailyTaskManager.record(serverPlayer, DailyTaskEvent.of(
                        DailyTaskEvent.Type.USE_BLOCK,
                        DailyTargetId.of(Blocks.FLETCHING_TABLE)
                ));
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            if (entity instanceof Animal animal && animal.isFood(player.getItemInHand(hand))) {
                DailyTaskManager.record(serverPlayer, DailyTaskEvent.of(
                        DailyTaskEvent.Type.FEED_ENTITY,
                        DailyTargetId.of(animal.getType())
                ));
            } else if (entity instanceof ItemFrame && player.getItemInHand(hand).is(Items.GLOW_INK_SAC)) {
                DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.MODIFY_ITEM_FRAME));
            }
            return InteractionResult.PASS;
        });
    }

    private static void onDamage(
            LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source,
            float baseDamageTaken,
            float damageTaken,
            boolean blocked
    ) {
        if (!(entity instanceof ServerPlayer player) || damageTaken <= 0.0F || !player.isAlive()) return;

        source.typeHolder().unwrapKey().ifPresent(key -> DailyTaskManager.record(
                player,
                new DailyTaskEvent(DailyTaskEvent.Type.TAKE_DAMAGE, "type", key.identifier().toString(), 1)
        ));

        Entity causingEntity = source.getEntity();
        if (causingEntity != null) {
            DailyTaskManager.record(player, new DailyTaskEvent(
                    DailyTaskEvent.Type.TAKE_DAMAGE,
                    "entity",
                    BuiltInRegistries.ENTITY_TYPE.getKey(causingEntity.getType()).toString(),
                    1
            ));
        }
    }
}
