package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        FakeItemsCommand.init();
        MainModRecipes.register();
    }
}