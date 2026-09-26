package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerPotions;

@Mixin(PotionContents.class)
abstract class PlayerPotionConsumeMixin {
    @Inject(method = "onConsume", at = @At("TAIL"))
    private void mainmod$drink(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        String identity = PlayerPotions.identity(stack);
        if (identity != null) PlayerDisguises.apply(entity, identity, PlayerPotions.duration(identity));
    }
}
