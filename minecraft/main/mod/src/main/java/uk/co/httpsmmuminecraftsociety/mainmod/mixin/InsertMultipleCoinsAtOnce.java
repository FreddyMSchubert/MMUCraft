package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(ResultSlot.class)
public class InsertMultipleCoinsAtOnce
{
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Unique
    private boolean mainmod$walletInsertCraft;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void mainmod$captureWalletRecipe(Player player, ItemStack craftedStack, CallbackInfo ci) {
        this.mainmod$walletInsertCraft = WalletCharm.isWalletInsertGrid(this.craftSlots);
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int inputCoins = 0;
        boolean hasCoin = DabloonInteractionLog.kind(craftedStack).equals("coin");
        boolean hasWallet = DabloonInteractionLog.kind(craftedStack).equals("wallet");
        StringBuilder inputs = new StringBuilder();
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack ingredient = this.craftSlots.getItem(i);
            String kind = DabloonInteractionLog.kind(ingredient);
            if (kind.isEmpty()) continue;
            if (kind.equals("coin")) {
                hasCoin = true;
                inputCoins += DabloonInteractionLog.value(ingredient);
            } else {
                hasWallet = true;
            }
            if (!inputs.isEmpty()) inputs.append(", ");
            inputs.append(DabloonInteractionLog.itemId(ingredient)).append(" x").append(ingredient.getCount());
            if (kind.equals("wallet")) inputs.append(" (balance ").append(DabloonInteractionLog.value(ingredient)).append(")");
        }
        if (!hasCoin && !hasWallet) return;
        int amount = Math.max(inputCoins,
                DabloonInteractionLog.kind(craftedStack).equals("coin") ? DabloonInteractionLog.value(craftedStack) : 0);
        DabloonInteractionLog.record(serverPlayer, "craft", hasCoin && hasWallet ? "mixed" : hasCoin ? "coin" : "wallet",
                DabloonInteractionLog.itemId(craftedStack),
                craftedStack.getCount(), amount, null, null,
                "Inputs: " + inputs + "; output: " + DabloonInteractionLog.itemId(craftedStack)
                        + " x" + craftedStack.getCount() + " (value " + DabloonInteractionLog.value(craftedStack) + ")");
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void mainmod$consumeAllCoins(Player player, ItemStack craftedStack, CallbackInfo ci) {
        if (!this.mainmod$walletInsertCraft) {
            return;
        }

        this.mainmod$walletInsertCraft = false;

        if (WalletCharm.clearRemainingCoinStacks(this.craftSlots)) {
            player.containerMenu.slotsChanged(this.craftSlots);
            player.containerMenu.broadcastChanges();
        }
    }
}
