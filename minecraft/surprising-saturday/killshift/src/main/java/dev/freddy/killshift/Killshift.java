package dev.freddy.killshift;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class Killshift implements ModInitializer {
    public static final String MOD_ID = "killshift";

    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ShapeManager::onDeath);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(MobAbilities::onDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShapeManager::allowDamage);
        AttackEntityCallback.EVENT.register(ShapeManager::onAttack);
        UseItemCallback.EVENT.register(MobAbilities::onUseItem);
        UseBlockCallback.EVENT.register(MobAbilities::onUseBlock);
        ServerTickEvents.END_SERVER_TICK.register(ShapeManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EventApi.refreshPresentation(handler.getPlayer()));
    }
}
