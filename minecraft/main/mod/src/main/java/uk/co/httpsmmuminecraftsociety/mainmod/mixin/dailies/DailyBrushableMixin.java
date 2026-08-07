package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(BrushableBlockEntity.class)
public abstract class DailyBrushableMixin {
    @Shadow private int brushCount;
    @Unique private int mainmod$previousBrushCount;
    @Unique private String mainmod$brushedBlock = "";

    @Inject(method = "brush", at = @At("HEAD"))
    private void mainmod$captureBrush(
            long gameTime,
            ServerLevel level,
            LivingEntity user,
            Direction direction,
            ItemStack brush,
            CallbackInfoReturnable<Boolean> cir
    ) {
        mainmod$previousBrushCount = brushCount;
        mainmod$brushedBlock = BuiltInRegistries.BLOCK.getKey(((BrushableBlockEntity)(Object)this).getBlockState().getBlock()).toString();
    }

    @Inject(method = "brush", at = @At("RETURN"))
    private void mainmod$recordBrush(
            long gameTime,
            ServerLevel level,
            LivingEntity user,
            Direction direction,
            ItemStack brush,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (user instanceof ServerPlayer player && brushCount > mainmod$previousBrushCount) {
            DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.BRUSH_BLOCK, mainmod$brushedBlock));
        }
    }
}
