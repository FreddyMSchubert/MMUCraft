package uk.co.httpsmmuminecraftsociety.mainmod.mixin;


import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;

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
