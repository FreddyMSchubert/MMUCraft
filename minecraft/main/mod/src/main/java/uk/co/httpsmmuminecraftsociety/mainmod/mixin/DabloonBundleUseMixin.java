package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(BundleItem.class)
public abstract class DabloonBundleUseMixin {
    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void mainmod$beforeBundleUse(Level level, LivingEntity user, ItemStack stack, int ticksRemaining,
                                         CallbackInfo ci, @Share("before") LocalRef<ItemStack> before) {
        if (user instanceof ServerPlayer && !DabloonInteractionLog.kind(stack).isEmpty()) before.set(stack.copy());
    }

    @Inject(method = "onUseTick", at = @At("RETURN"))
    private void mainmod$afterBundleUse(Level level, LivingEntity user, ItemStack stack, int ticksRemaining,
                                        CallbackInfo ci, @Share("before") LocalRef<ItemStack> before) {
        if (!(user instanceof ServerPlayer player) || before.get() == null) return;
        ItemStack original = before.get();
        String earlier = DabloonInteractionLog.describe(original);
        String now = DabloonInteractionLog.describe(stack);
        if (earlier.equals(now)) return;
        int amount = Math.abs(DabloonInteractionLog.value(original) - DabloonInteractionLog.value(stack));
        DabloonInteractionLog.record(player, "transfer", "container", DabloonInteractionLog.itemId(original),
                original.getCount(), amount, null, null,
                "Bundle use: " + earlier + " -> " + (now.isEmpty() ? "empty" : now));
    }
}
