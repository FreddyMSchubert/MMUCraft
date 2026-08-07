package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.DataLoader;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyEvents;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskRegistry;
import uk.co.httpsmmuminecraftsociety.mainmod.beacon.DynamicBeaconRange;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.PickaxeHeaterCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.SoulboundEnchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GrpcBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.CharmEnchanting;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FoodModifier;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LootTableModifiers;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final String RESOURCE_PACK_ID = "mmu_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ResourceKey<PlacedFeature> ALIEN_DEBRIS = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "alien_debris")
    );

    private static volatile HolderLookup.Provider registries;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        BiomeModifications.addFeature(
                BiomeSelectors.foundInTheEnd(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ALIEN_DEBRIS
        );

        DataLoader.init();
        DailyTaskRegistry.validate();

        FakeItemsCommand.init();
        MoneyCommand.init();
        WebsiteCommand.init();
        PlayerStatsSync.init();
        ClaimsManager.init();
        DailyEvents.register();
        MainModRecipes.register();

        ServerLifecycleEvents.SERVER_STARTED.register(this::registerGamerules);
        ServerTickEvents.END_LEVEL_TICK.register(CharmsManager::onPlayerTick);
        ItemEvents.USE.register(CharmsManager::onItemUse);
        ItemEvents.USE_ON.register(DecoBlocksManager::onUseItemOn);
        ItemEvents.USE_ON.register(AnvilLogic::onUseItemOn);
        UseEntityCallback.EVENT.register(CharmsManager::onUseEntity);
        UseBlockCallback.EVENT.register(DynamicBeaconRange::onUseBlock);
        UseBlockCallback.EVENT.register(DecoBlocksManager::onUseBlock);
        UseBlockCallback.EVENT.register(CosmeticsManager::onUseBlock);
        UseBlockCallback.EVENT.register(CharmsManager::onUseBlock);
        ServerPlayerEvents.COPY_FROM.register(SoulboundEnchantment::onCopyFrom);
        LootTableEvents.MODIFY_DROPS.register(LootTableModifiers::onModifyDrops);
        DefaultItemComponentEvents.MODIFY.register(FoodModifier::onDefaultItemComponentsModify);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(TeleportPotionUtils::onLivingEntityDamage);
        EnchantmentEvents.ALLOW_ENCHANTING.register(CharmEnchanting::onAllowEnchanting);
        PlayerBlockBreakEvents.AFTER.register(CharmsManager::onAfterBlockBreak);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> ClaimsManager.reset());
        ServerLifecycleEvents.SERVER_STARTED.register(GrpcBridge::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GrpcBridge.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GrpcBridge.onServerTick();
            PlayerStatsSync.onServerTick(server);
            ClaimsManager.tickBossBars(server);
            DailyTaskManager.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            registries = server.overworld().registryAccess();
            EnchantmentSettingsManager.validateLoottables(server.reloadableRegistries().lookup());
            PickaxeHeaterCharm.rebuildSmeltedDropMap(server);
            FakeItems.validate();
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                registries = server.overworld().registryAccess();
                EnchantmentSettingsManager.validateLoottables(server.reloadableRegistries().lookup());
                PickaxeHeaterCharm.rebuildSmeltedDropMap(server);
            }
        });
    }

    private void registerGamerules(MinecraftServer server)
    {
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().set(GameRules.MAX_MINECART_SPEED, 20, server);
            // level.getGameRules().set(GameRules.REDUCED_DEBUG_INFO, true, server);
            level.getGameRules().set(GameRules.SPAWN_PHANTOMS, false, server);
        }
    }

    public static HolderLookup.Provider getRegistries() {
        HolderLookup.Provider value = registries;
        if (value == null) {
            throw new IllegalStateException(
                    "Runtime registries are not available yet. Tried to create a registry-backed fake item before the server was ready."
            );
        }
        return value;
    }
}
