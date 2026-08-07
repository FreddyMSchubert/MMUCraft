package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(ItemStack.class)
public abstract class DailyFoodMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void mainmod$recordDailyFood(
            Level level,
            LivingEntity entity,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        if (entity instanceof ServerPlayer player && stack.has(DataComponents.FOOD)) {
            DailyTaskManager.onEat(player, stack);
        }
    }
}
