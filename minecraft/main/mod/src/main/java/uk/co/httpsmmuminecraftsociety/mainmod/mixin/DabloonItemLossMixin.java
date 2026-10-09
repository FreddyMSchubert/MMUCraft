package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(ItemEntity.class)
public abstract class DabloonItemLossMixin {
    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void mainmod$recordMerge(ItemEntity into, ItemStack intoStack, ItemEntity from, ItemStack fromStack,
                                            CallbackInfo ci) {
        if (DabloonInteractionLog.kind(fromStack).isEmpty()) return;
        DabloonInteractionLog.recordWorld("transfer", DabloonInteractionLog.kind(fromStack),
                DabloonInteractionLog.itemId(fromStack), fromStack.getCount(), DabloonInteractionLog.value(fromStack),
                into.level().dimension().identifier().toString(), into.blockPosition().getX(),
                into.blockPosition().getY(), into.blockPosition().getZ(),
                "Item entity merge " + from.getUUID() + " -> " + into.getUUID()
                        + "; contents: " + DabloonInteractionLog.describe(fromStack));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mainmod$recordDespawn(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity)(Object)this;
        if (!(entity.level() instanceof ServerLevel level) || !entity.isRemoved() || entity.getAge() < 6000) return;
        ItemStack stack = entity.getItem();
        if (DabloonInteractionLog.kind(stack).isEmpty()) return;
        DabloonInteractionLog.recordWorld("despawn", stack, level.dimension().identifier().toString(),
                entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(),
                "Item entity " + entity.getUUID() + "; contents: " + DabloonInteractionLog.describe(stack));
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void mainmod$beforeDamage(ServerLevel level, DamageSource source, float damage,
                                      CallbackInfoReturnable<Boolean> cir, @Share("before") LocalRef<ItemStack> before) {
        ItemStack stack = ((ItemEntity)(Object)this).getItem();
        if (!DabloonInteractionLog.kind(stack).isEmpty()) before.set(stack.copy());
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void mainmod$afterDamage(ServerLevel level, DamageSource source, float damage,
                                     CallbackInfoReturnable<Boolean> cir, @Share("before") LocalRef<ItemStack> before) {
        ItemEntity entity = (ItemEntity)(Object)this;
        ItemStack stack = before.get();
        if (stack == null || !entity.isRemoved() || !cir.getReturnValue()) return;
        String context = "Item entity " + entity.getUUID() + "; damage=" + source.getMsgId()
                + (source.getEntity() == null ? "" : "; caused_by=" + source.getEntity().getUUID())
                + "; contents: " + DabloonInteractionLog.describe(stack);
        if (source.getEntity() instanceof ServerPlayer player) {
            DabloonInteractionLog.record(player, "destroy", DabloonInteractionLog.kind(stack),
                    DabloonInteractionLog.itemId(stack), stack.getCount(), DabloonInteractionLog.value(stack),
                    null, null, context);
        } else {
            DabloonInteractionLog.recordWorld("destroy", stack, level.dimension().identifier().toString(),
                    entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(), context);
        }
    }
}
