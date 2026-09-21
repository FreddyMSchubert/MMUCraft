package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.MapInvisibility;

@Mixin(EmptyMapItem.class)
public abstract class SmallEmptyMapUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mainmod$fillResizedMap(Level level, Player player, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResult result = SmallMaps.useResizedEmptyMap(level, player, stack);
        if (result == null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            result = MapInvisibility.useTaggedEmptyMap(serverLevel, player, stack);
        }
        if (result != null) cir.setReturnValue(result);
    }
}
