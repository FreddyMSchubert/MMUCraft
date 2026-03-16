package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.connection.AuthManager;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.SoulboundEnchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FoodModifier;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LootTableModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final String RESOURCE_PACK_ID = "mmu_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final AuthManager AUTH_MANAGER = new AuthManager();

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        ServerTickEvents.END_WORLD_TICK.register(CharmsManager::onPlayerTick);
        ItemEvents.USE.register(CharmsManager::onItemUse);
        UseBlockCallback.EVENT.register(CosmeticsManager::onUseBlock);
        ServerPlayerEvents.COPY_FROM.register(SoulboundEnchantment::onCopyFrom);
        LootTableEvents.MODIFY.register(LootTableModifiers::onModify);
        LootTableEvents.MODIFY_DROPS.register(LootTableModifiers::onModifyDrops);
        DefaultItemComponentEvents.MODIFY.register(FoodModifier::onDefaultItemComponentsModify);

        FakeItemsCommand.init();
        MainModRecipes.register();
        AUTH_MANAGER.onInitialize();
    }
}
