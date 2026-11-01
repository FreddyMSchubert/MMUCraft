package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

@Mixin(ServerPlayer.class)
public abstract class StrawBedDaySleepMixin {
    @ModifyVariable(method = "startSleepInBed", at = @At("HEAD"), argsOnly = true)
    private BedRule mainmod$allowStrawBedDaySleep(BedRule rule, AbstractBedBlock bed,
                                                  BlockState state, BedRule originalRule, BlockPos pos) {
        if (!state.is(Blocks.STRAW_BED) || rule.canSleep() != BedRule.Rule.WHEN_DARK) return rule;

        ServerPlayer player = (ServerPlayer) (Object) this;
        return new BedRule(player.level().isDarkOutside() ? BedRule.Rule.NEVER : BedRule.Rule.ALWAYS,
                rule.canSetSpawn(), rule.destroyOnUse(), rule.destroyOnLeave(),
                Optional.of(Component.literal("You can only sleep in a straw bed during daylight.")));
    }
}
