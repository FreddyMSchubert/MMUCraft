package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(ServerLevel.class)
public abstract class DabloonItemSpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void mainmod$recordCurrencySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(entity instanceof ItemEntity item)) return;
        ItemStack stack = item.getItem();
        if (DabloonInteractionLog.kind(stack).isEmpty()) return;
        ServerLevel level = (ServerLevel)(Object)this;
        ServerPlayer owner = item.getOwner() instanceof ServerPlayer player ? player : DabloonInteractionLog.currentDropActor();
        if (owner != null) {
            DabloonInteractionLog.record(owner, "drop", DabloonInteractionLog.kind(stack),
                    DabloonInteractionLog.itemId(stack), stack.getCount(), DabloonInteractionLog.value(stack),
                    null, null, "Item entity " + item.getUUID() + "; contents: " + DabloonInteractionLog.describe(stack));
        } else {
            DabloonInteractionLog.recordWorld("drop", stack, level.dimension().identifier().toString(),
                    item.blockPosition().getX(), item.blockPosition().getY(), item.blockPosition().getZ(),
                    "Item entity " + item.getUUID() + "; contents: " + DabloonInteractionLog.describe(stack));
        }
    }
}
