package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(EnchantedItemTrigger.class)
public abstract class DailyEnchantedItemMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordEnchant(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        DailyTaskManager.recordEnchantedItem(player, stack, true);
    }
}
