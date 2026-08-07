package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(EnchantedItemTrigger.class)
public abstract class DailyEnchantedItemMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordEnchant(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.ENCHANT_AT_TABLE));
        EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().forEach(enchantment ->
                enchantment.unwrapKey().ifPresent(key -> DailyTaskManager.record(
                        player,
                        DailyTaskEvent.of(DailyTaskEvent.Type.ENCHANT_AT_TABLE, key.identifier().toString())
                ))
        );
    }
}
