package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class StrawBedSleepTickMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean mainmod$keepDaytimeStrawSleep(BedRule rule, Level level, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        if (player.getInBlockState().is(Blocks.STRAW_BED) && rule.canSleep() == BedRule.Rule.WHEN_DARK) {
            return !level.isDarkOutside();
        }
        return original.call(rule, level);
    }
}
