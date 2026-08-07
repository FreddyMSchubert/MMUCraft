package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.Collection;

@Mixin(FishingRodHookedTrigger.class)
public abstract class DailyFishingMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordCatch(
            ServerPlayer player,
            ItemStack rod,
            FishingHook hook,
            Collection<ItemStack> catches,
            CallbackInfo ci
    ) {
        if (catches.isEmpty()) return;
        DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.FISH));
        for (ItemStack stack : catches) {
            if (!stack.isEmpty()) {
                FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
                DailyTaskManager.record(player, new DailyTaskEvent(
                        DailyTaskEvent.Type.FISH,
                        fakeItem == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : fakeItem.id(),
                        "",
                        stack.getCount()
                ));
            }
        }
    }
}
