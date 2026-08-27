package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(MerchantResultSlot.class)
public abstract class DailyMerchantResultMixin {
    @Shadow @Final private MerchantContainer slots;
    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void mainmod$recordTrade(net.minecraft.world.entity.player.Player player, ItemStack result, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        MerchantOffer offer = slots.getActiveOffer();
        if (offer == null) return;
        ItemStack traded = offer.getResult();

        if (traded.is(Items.EMERALD)) {
            record(serverPlayer, "receive_emeralds", "", traded.getCount());
        }
        int emeraldsSpent = emeraldCount(offer.getCostA()) + emeraldCount(offer.getCostB());
        if (emeraldsSpent > 0) record(serverPlayer, "spend_emeralds", "", emeraldsSpent);

        record(serverPlayer, "receive_item", BuiltInRegistries.ITEM.getKey(traded.getItem()).toString(), traded.getCount());
        recordCost(serverPlayer, offer.getCostA());
        recordCost(serverPlayer, offer.getCostB());

        if (merchant instanceof Villager villager) {
            villager.getVillagerData().profession().unwrapKey().ifPresent(key ->
                    record(serverPlayer, "profession", key.identifier().toString(), 1)
            );
        } else if (merchant instanceof WanderingTrader) {
            record(serverPlayer, "wandering_trader", "", 1);
        }
    }

    private static int emeraldCount(ItemStack stack) {
        return stack.is(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static void recordCost(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            record(player, "give_item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
        }
    }

    private static void record(ServerPlayer player, String subject, String secondary, int amount) {
        DailyTaskManager.record(player, new DailyTaskEvent(
                DailyTaskEvent.Type.VILLAGER_TRADE,
                subject,
                secondary,
                amount
        ));
    }
}
