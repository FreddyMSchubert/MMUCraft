package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemsCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.CandleOfTheDeepCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.RunningShoesCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.OpenHeartCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

import java.util.List;

public class MainMod implements ModInitializer {
	public static final String MOD_ID = "mainmod";
	public static final String RESOURCE_PACK_ID = "mmu_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello MMU!");

        ServerTickEvents.END_WORLD_TICK.register(MainMod::onPlayerTick);

        FakeItemsCommand.init();
        MainModRecipes.register();
    }

    private static ItemStack tickItemStackIfIsCharm(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return stack;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd.isEmpty()) return stack;
        CompoundTag tag = cd.copyTag();
        if (!tag.getBooleanOr(Utils.TAG_TICK, false)) return stack;

        if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) return stack;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
        if (cmd.strings().isEmpty() || cmd.strings().getFirst().isEmpty()) return stack;
        switch (cmd.strings().getFirst()) {
            case CandleOfTheDeepCharm.CANDLE_OF_THE_DEEP_CHARM_ID:
                stack = new CandleOfTheDeepCharm().onTick(stack, player, level);
                break;
            case OpenHeartCharm.OPEN_HEART_CHARM_ID:
                stack = new OpenHeartCharm().onTick(stack, player, level);
                break;
            case RunningShoesCharm.FROST_WALKER_CHARM_ID:
                stack = new RunningShoesCharm().onTick(stack, player, level);
                break;
        }
        return stack;
    }
    private static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players())
            for (EquipmentSlot slot : EquipmentSlot.values())
                player.setItemSlot(slot, tickItemStackIfIsCharm(player.getItemBySlot(slot), player, server));
    }
}