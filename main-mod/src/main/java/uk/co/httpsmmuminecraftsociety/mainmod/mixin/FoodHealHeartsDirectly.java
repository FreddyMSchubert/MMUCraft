package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.FoodModifier;

@Mixin(LivingEntity.class)
public abstract class FoodHealHeartsDirectly {
    @Shadow public abstract ItemStack getUseItem();

    @Unique
    private ItemStack mainmod$foodStackBeforeUse = ItemStack.EMPTY;

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void mainmod$captureFoodStack(CallbackInfo ci) {
        this.mainmod$foodStackBeforeUse = this.getUseItem().copy();
    }

    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void mainmod$applyDirectFoodHealing(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        ServerPlayer serverPlayer = (ServerPlayer) self;

        if (serverPlayer.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }

        if (this.mainmod$foodStackBeforeUse.isEmpty()) {
            return;
        }

        float directHearts = FoodModifier.directHearts(this.mainmod$foodStackBeforeUse.getItem());
        if (directHearts > 0.0F) {
            player.heal(directHearts * 2.0F);
        }

        this.mainmod$foodStackBeforeUse = ItemStack.EMPTY;
    }
}
