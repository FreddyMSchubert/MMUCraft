package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DabloonRightClickMixin {
    @Shadow public ServerPlayer player;
    @Shadow public abstract boolean hasClientLoaded();

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z", shift = At.Shift.AFTER))
    private void mainmod$airRightClick(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (hasClientLoaded()) record(packet.hand(), "air");
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z", shift = At.Shift.AFTER))
    private void mainmod$blockRightClick(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (hasClientLoaded() && player.isWithinBlockInteractionRange(packet.hitResult().getBlockPos(), 1.0)) {
            record(packet.hand(), "block=" + packet.hitResult().getBlockPos().toShortString()
                    + "; face=" + packet.hitResult().getDirection());
        }
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z", shift = At.Shift.AFTER))
    private void mainmod$entityRightClick(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (!hasClientLoaded()) return;
        Entity target = player.level().getEntityOrPart(packet.entityId());
        if (target != null && player.isWithinEntityInteractionRange(target.getBoundingBox(), 3.0)) {
            record(packet.hand(), "entity=" + target.getType() + "; uuid=" + target.getUUID());
        }
    }

    private void record(InteractionHand hand, String target) {
        ItemStack stack = player.getItemInHand(hand);
        String kind = DabloonInteractionLog.kind(stack);
        if (kind.isEmpty()) return;
        DabloonInteractionLog.record(player, "right_click", kind, DabloonInteractionLog.itemId(stack),
                stack.getCount(), DabloonInteractionLog.value(stack), null, null,
                "Hand=" + hand + "; target=" + target + "; contents: " + DabloonInteractionLog.describe(stack));
    }
}
