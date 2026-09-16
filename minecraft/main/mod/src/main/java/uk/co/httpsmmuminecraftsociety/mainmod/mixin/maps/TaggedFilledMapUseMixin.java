package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.MapInvisibility;
import uk.co.httpsmmuminecraftsociety.mainmod.maps.SmallMaps;

@Mixin(Item.class)
public abstract class TaggedFilledMapUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mainmod$updateTaggedMap(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this != Items.FILLED_MAP) return;
        ItemStack map = player.getItemInHand(hand);
        if (MapInvisibility.target(map) == null) return;
        if (level instanceof ServerLevel serverLevel) {
            if (SmallMaps.isSmall(map)) {
                SmallMaps.refreshSmallMap(map, serverLevel);
            } else {
                for (int column = 0; column < 16; column++) {
                    MapInvisibility.updateVanillaMap(map, serverLevel, player);
                }
            }
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
