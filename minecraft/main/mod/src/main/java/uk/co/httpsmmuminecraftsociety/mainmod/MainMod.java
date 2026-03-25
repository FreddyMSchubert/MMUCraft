package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.connection.AuthManager;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.SoulboundEnchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FoodModifier;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LootTableModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final String RESOURCE_PACK_ID = "mmu_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final AuthManager AUTH_MANAGER = new AuthManager();

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        FakeItemsCommand.init();
        MainModRecipes.register();
        AUTH_MANAGER.onInitialize();

        ServerLifecycleEvents.SERVER_STARTED.register(this::registerContent);
    }

    private void registerContent(MinecraftServer server)
    {
        // gamerules
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().set(GameRules.MAX_MINECART_SPEED, 20, server);
            level.getGameRules().set(GameRules.REDUCED_DEBUG_INFO, true, server);
            level.getGameRules().set(GameRules.SPAWN_PHANTOMS, false, server);
        }

        // content
        DataLoader.init();

        ServerTickEvents.END_LEVEL_TICK.register(CharmsManager::onPlayerTick);
        ItemEvents.USE.register(CharmsManager::onItemUse);
        UseBlockCallback.EVENT.register(CosmeticsManager::onUseBlock);
        ServerPlayerEvents.COPY_FROM.register(SoulboundEnchantment::onCopyFrom);
        LootTableEvents.MODIFY.register(LootTableModifiers::onModify);
        LootTableEvents.MODIFY_DROPS.register(LootTableModifiers::onModifyDrops);
        DefaultItemComponentEvents.MODIFY.register(FoodModifier::onDefaultItemComponentsModify);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(TeleportPotionUtils::onLivingEntityDamage);
    }
}
