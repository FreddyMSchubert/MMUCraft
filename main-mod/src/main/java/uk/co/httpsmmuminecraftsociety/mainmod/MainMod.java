package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantmentEffects;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final String RESOURCE_PACK_ID = "mmu_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        ServerTickEvents.END_WORLD_TICK.register(CharmsManager::onPlayerTick);
        ItemEvents.USE.register(CharmsManager::onItemUse);


        FakeItemsCommand.init();
        MainModRecipes.register();
        ModEnchantmentEffects.registerModEnchantmentEffects();
    }
}
