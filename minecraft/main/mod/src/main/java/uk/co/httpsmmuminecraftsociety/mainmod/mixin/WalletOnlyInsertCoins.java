package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.WalletUtils;

@Mixin(BundleItem.class)
public abstract class WalletOnlyInsertCoins
{
    @Shadow
    protected abstract void broadcastChangesOnContainerMenu(Player player);

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
    private void stackedOnOther(
            ItemStack bundleStack,
            Slot slot,
            ClickAction clickAction,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        MainMod.LOGGER.info("overrideStackedOnOther HEAD called!");
        if (!WalletUtils.isWallet(bundleStack)) {
            MainMod.LOGGER.info("overrideStackedOnOther HEAD: not a wallet");
            return;
        }
        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty())
        {
            MainMod.LOGGER.info("overrideStackedOnOther HEAD: slotstack (the stack wallet was moved to) is empty");
            return;
        }
        if (!WalletUtils.isCoin(ItemStackTemplate.fromNonEmptyStack(slotStack))) {
            MainMod.LOGGER.info("overrideStackedOnOther HEAD: item wasnt a coin - returning false!");
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void stackedOnByOther(
            ItemStack bundleStack,
            ItemStack carriedStack,
            Slot slot,
            ClickAction clickAction,
            Player player,
            SlotAccess slotAccess,
            CallbackInfoReturnable<Boolean> cir
    ) {
        MainMod.LOGGER.info("overrideOtherStackedOnMe HEAD called!");
        if (!WalletUtils.isWallet(bundleStack)) {
            MainMod.LOGGER.info("overrideOtherStackedOnMe HEAD: not a wallet");
            return;
        }
        if (carriedStack.isEmpty())
        {
            MainMod.LOGGER.info("overrideOtherStackedOnMe HEAD: carried stack is empty");
            return;
        }
        if (!WalletUtils.isCoin(ItemStackTemplate.fromNonEmptyStack(carriedStack))) {
            MainMod.LOGGER.info("overrideOtherStackedOnMe HEAD: item wasnt a coin - returning false!");
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "overrideStackedOnOther", at = @At("RETURN"))
    private void stackedOnOtherReturn(
            ItemStack bundleStack,
            Slot slot,
            ClickAction clickAction,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        MainMod.LOGGER.info("overrideStackedOnOther RETURN called!");
        if (!cir.getReturnValueZ()) return;
        WalletUtils.sortWallet(bundleStack);
        broadcastChangesOnContainerMenu(player);
    }

    @Inject(method = "overrideOtherStackedOnMe", at = @At("RETURN"))
    private void stackedOnByOtherReturn(
            ItemStack bundleStack,
            ItemStack carriedStack,
            Slot slot,
            ClickAction clickAction,
            Player player,
            SlotAccess slotAccess,
            CallbackInfoReturnable<Boolean> cir
    ) {
        MainMod.LOGGER.info("overrideOtherStackedOnMe RETURN called!");
        if (!cir.getReturnValueZ()) return;
        WalletUtils.sortWallet(bundleStack);
        broadcastChangesOnContainerMenu(player);
    }
}
