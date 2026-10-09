package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(LivingEntity.class)
public abstract class DabloonPlayerDropMixin {
    @WrapMethod(method = "drop")
    private @Nullable ItemEntity mainmod$attributePlayerDrop(ItemStack stack, boolean thrownFromHand,
                                                               Prediction prediction, Operation<ItemEntity> original) {
        if ((Object)this instanceof ServerPlayer player && !DabloonInteractionLog.kind(stack).isEmpty()) {
            return DabloonInteractionLog.withActor(player, () -> original.call(stack, thrownFromHand, prediction));
        }
        return original.call(stack, thrownFromHand, prediction);
    }
}
