package dev.freddy.killshift;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class Killshift implements ModInitializer {
    public static final String MOD_ID = "killshift";

    @Override
    public void onInitialize() {
        ShiftCommand.register();
        ServerPlayerEvents.AFTER_RESPAWN.register(NearbyRespawn::afterRespawn);
        ServerPlayerEvents.AFTER_RESPAWN.register(ShapeManager::afterRespawn);
        ServerLivingEntityEvents.AFTER_DEATH.register(ShapeManager::onDeath);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(MobAbilities::onDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShapeManager::allowDamage);
        AttackEntityCallback.EVENT.register(ShapeManager::onAttack);
        UseEntityCallback.EVENT.register(ShapeManager::onUseEntity);
        UseItemCallback.EVENT.register(MobFood::onUseItem);
        UseItemCallback.EVENT.register(MobAbilities::onUseItem);
        UseBlockCallback.EVENT.register(MobFood::onUseBlock);
        UseBlockCallback.EVENT.register(MobAbilities::onUseBlock);
        UseEntityCallback.EVENT.register(MobAbilities::onUseEntity);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ShapeManager.tick(server);
            if (server.getTickCount() % (20 * 60) == 0) {
                server.getPlayerList().getPlayers().forEach(EventApi::refreshPresentation);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ShapeManager.playerJoined(handler.getPlayer());
            AbilitySlot.sync(handler.getPlayer());
            EventApi.playerJoined(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EventApi.playerLeft(handler.getPlayer()));
        EntityTrackingEvents.START_TRACKING.register(ShapeView::onStartTracking);
        ServerEntityEvents.ENTITY_LOAD.register(ShapeView::onEntityLoad);
        ServerEntityEvents.ENTITY_LOAD.register(GiantSpawns::onEntityLoad);
    }
}
