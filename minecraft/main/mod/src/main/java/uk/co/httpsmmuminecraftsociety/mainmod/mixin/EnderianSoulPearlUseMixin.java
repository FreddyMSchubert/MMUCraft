package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.EnderianSoulCharm;

@Mixin(EnderpearlItem.class)
public abstract class EnderianSoulPearlUseMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void mainmod$maybeSavePearl(ItemStack pearl, int count, LivingEntity thrower) {
        if (thrower instanceof ServerPlayer player) {
            int chance = EnderianSoulCharm.pearlSavePercent(
                    CharmsManager.getPlayerCharmLevel(player, EnderianSoulCharm.class));
            if (player.getRandom().nextInt(100) < chance) return;
        }
        pearl.consume(count, thrower);
    }
}
