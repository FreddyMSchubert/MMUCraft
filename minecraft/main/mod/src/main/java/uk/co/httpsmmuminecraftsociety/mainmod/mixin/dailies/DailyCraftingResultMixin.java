package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.SculkPhialCharm;

@Mixin(ResultSlot.class)
public abstract class DailyCraftingResultMixin {
    @Shadow @Final private net.minecraft.world.entity.player.Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void mainmod$recordCraft(ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, new DailyTaskEvent(
                    DailyTaskEvent.Type.CRAFT_ITEM,
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    "",
                    Math.max(1, removeCount)
            ));
            if (stack.is(Items.EXPERIENCE_BOTTLE)
                    && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .copyTag().getIntOr(SculkPhialCharm.XP_STORED_ID, 0) > 0) {
                MasteryAdvancements.grant(serverPlayer, "utility/sculk_phial_extract");
            }
        }
    }
}
