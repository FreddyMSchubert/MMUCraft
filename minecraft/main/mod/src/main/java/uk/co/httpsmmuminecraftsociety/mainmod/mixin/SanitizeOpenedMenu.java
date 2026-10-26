package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;

@Mixin(ServerPlayer.class)
public abstract class SanitizeOpenedMenu {
    @Inject(method = "initMenu", at = @At("HEAD"))
    private void mainmod$sanitizeOpenedInventory(AbstractContainerMenu menu, CallbackInfo ci) {
        // Snapshot existing slots before sanitation. The listener added by initMenu then sees only real changes.
        if (menu != ((ServerPlayer) (Object) this).inventoryMenu) menu.broadcastChanges();
        GearEnchantmentSanitizer.sanitizeMenu(menu);
    }
}
