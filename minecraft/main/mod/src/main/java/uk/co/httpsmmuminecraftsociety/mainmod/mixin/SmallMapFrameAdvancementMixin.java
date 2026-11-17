package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

@Mixin(ItemFrame.class)
public abstract class SmallMapFrameAdvancementMixin {
    @Unique private boolean mainmod$wasEmpty;

    @Inject(method = "interact", at = @At("HEAD"))
    private void mainmod$beforeMapInsert(Player player, InteractionHand hand, Vec3 hit,
                                         CallbackInfoReturnable<InteractionResult> callback) {
        mainmod$wasEmpty = ((ItemFrame) (Object) this).getItem().isEmpty();
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void mainmod$afterMapInsert(Player player, InteractionHand hand, Vec3 hit,
                                        CallbackInfoReturnable<InteractionResult> callback) {
        if (!(player instanceof ServerPlayer serverPlayer) || !mainmod$wasEmpty
                || !callback.getReturnValue().consumesAction()) return;
        var frame = (ItemFrame) (Object) this;
        if (SmallMaps.isMap(frame.getItem()) && SmallMaps.size(frame.getItem(), player.level()) == 16) {
            MasteryAdvancements.grant(serverPlayer, "utility/resourceful_resource_pack");
        }
    }
}
