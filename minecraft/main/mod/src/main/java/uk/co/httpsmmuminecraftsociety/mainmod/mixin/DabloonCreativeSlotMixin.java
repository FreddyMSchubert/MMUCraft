package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DabloonCreativeSlotMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
    private void mainmod$recordCreativeEdit(Slot slot, ItemStack replacement, Operation<Void> original) {
        ItemStack before = slot.getItem().copy();
        int balanceBefore = MoneyHelper.GetBalance(player);
        original.call(slot, replacement);
        ItemStack after = slot.getItem();
        String first = DabloonInteractionLog.kind(before);
        String second = DabloonInteractionLog.kind(after);
        if (first.isEmpty() && second.isEmpty()) return;
        String kind = first.equals("container") || second.equals("container") ? "container"
                : !first.isEmpty() && !second.isEmpty() && !first.equals(second) ? "mixed"
                : first.isEmpty() ? second : first;
        DabloonInteractionLog.record(player, "creative_edit", kind,
                !second.isEmpty() ? DabloonInteractionLog.itemId(after) : DabloonInteractionLog.itemId(before),
                !second.isEmpty() ? after.getCount() : before.getCount(),
                Math.max(DabloonInteractionLog.value(before), DabloonInteractionLog.value(after)),
                balanceBefore, MoneyHelper.GetBalance(player),
                "Creative slot=" + slot.index + "; "
                        + (first.isEmpty() ? "empty" : DabloonInteractionLog.describe(before)) + " -> "
                        + (second.isEmpty() ? "empty" : DabloonInteractionLog.describe(after)));
    }
}
