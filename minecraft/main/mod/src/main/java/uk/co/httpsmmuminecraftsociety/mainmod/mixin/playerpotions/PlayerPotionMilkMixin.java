package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;

@Mixin(Consumable.class)
abstract class PlayerPotionMilkMixin {
    @Inject(method = "onConsume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void mainmod$milk(Level level, LivingEntity entity, ItemStack stack,
                              CallbackInfoReturnable<ItemStack> cir) {
        if (stack.is(Items.MILK_BUCKET)) PlayerDisguises.milk(entity);
    }
}
