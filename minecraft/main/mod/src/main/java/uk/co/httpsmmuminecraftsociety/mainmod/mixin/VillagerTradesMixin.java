package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.DisabledDnTEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.FishMeatFishermanTrades;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.LecternLibrarianTrades;

@Mixin(Villager.class)
public abstract class VillagerTradesMixin extends AbstractVillager {
    protected VillagerTradesMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "updateTrades", at = @At("RETURN"))
    private void modifyGeneratedTrades(ServerLevel level, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        LecternLibrarianTrades.syncOffers(villager, level);
        FishMeatFishermanTrades.appendOffers(villager);
        if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)
                && villager.getVillagerData().level() == 1) {
            for (String id : new String[]{"corn", "grapes", "tomato"}) {
                if (villager.getRandom().nextInt(3) == 0) {
                    villager.getOffers().add(new MerchantOffer(
                            new ItemCost(Items.EMERALD, 3),
                            FakeItems.createFakeItemStack(id, 2),
                            16, 2, 0.05F));
                }
            }
        }
        mainmod$removeDisabledEnchantOffers(villager);
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void refreshLecternBookTrade(Player player, CallbackInfo ci) {
        if (this.level() instanceof ServerLevel serverLevel) {
            Villager villager = (Villager) (Object) this;
            LecternLibrarianTrades.syncOffers(villager, serverLevel);
            FishMeatFishermanTrades.appendOffers(villager);
            mainmod$removeDisabledEnchantOffers(villager);
        }
    }

    private static void mainmod$removeDisabledEnchantOffers(Villager villager) {
        villager.getOffers().removeIf(offer -> EnchantmentHelper.getEnchantmentsForCrafting(offer.getResult())
                .keySet().stream().anyMatch(DisabledDnTEnchantments::contains));
    }
}
