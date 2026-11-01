package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerLevel.class)
public abstract class StrawBedNightfallMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/clock/ServerClockManager$MoveResult;"),
            index = 1)
    private ResourceKey<ClockTimeMarker> mainmod$wakeAtNightfall(ResourceKey<ClockTimeMarker> marker) {
        return mainmod$hasStrawSleeper() ? ClockTimeMarkers.NIGHT : marker;
    }

    @ModifyExpressionValue(method = "announceSleepStatus", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent mainmod$announceSkippingDay(MutableComponent message) {
        return mainmod$hasStrawSleeper() ? Component.literal("Skipping day") : message;
    }

    @Unique
    private boolean mainmod$hasStrawSleeper() {
        ServerLevel level = (ServerLevel) (Object) this;
        return level.players().stream().anyMatch(player -> player.isSleeping()
                && player.getSleepingPos().map(pos -> level.getBlockState(pos).is(Blocks.STRAW_BED)).orElse(false));
    }
}
