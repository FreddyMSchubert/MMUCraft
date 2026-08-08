package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;

@Mixin(ServerPlayer.class)
public abstract class DailyUseStatMixin {
    @Inject(method = "awardStat", at = @At("HEAD"))
    private void mainmod$recordUsedItem(Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount < 1 || stat.getType() != Stats.ITEM_USED || !(stat.getValue() instanceof Item item)) return;
        DailyTaskManager.record((ServerPlayer)(Object)this, new DailyTaskEvent(
                DailyTaskEvent.Type.USE_ITEM,
                BuiltInRegistries.ITEM.getKey(item).toString(),
                "",
                amount
        ));
    }
}
