package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryAdvancements;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.magic.MagicFoodEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;

@Mixin(ItemStack.class)
public abstract class DailyFoodMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void mainmod$recordDailyFood(
            Level level,
            LivingEntity entity,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        if (entity instanceof ServerPlayer player && stack.has(DataComponents.FOOD)) {
            MagicFoodEffects.consumed(player, stack);
            DailyTaskManager.onEat(player, stack);
            if (FakeItems.isSpecificFakeItem(stack, "golden-nutritional-paste")) {
                MasteryAdvancements.grant(player, "utility/golden_paste");
                if (!level.getEntitiesOfClass(SulfurCube.class, player.getBoundingBox().inflate(8)).isEmpty()) {
                    MasteryAdvancements.grant(player, "utility/yellow_chakra_get");
                }
            }
            if (FakeItems.isSpecificFakeItem(stack, "charm-invisi-carrot")
                    && player.hasEffect(MobEffects.DARKNESS)) {
                MasteryAdvancements.grant(player, "utility/band_aid_fix");
            }
            if (FakeItems.isSpecificFakeItem(stack, "sushi")) {
                MasteryAdvancements.grant(player, "food/sushi");
            }
            if (FakeItems.isSpecificFakeItem(stack, "beer")) {
                if (player.hasEffect(MobEffects.NAUSEA)) MasteryAdvancements.grant(player, "food/beer_again");
                MasteryAdvancements.grant(player, "food/beer");
            }
        }
    }
}
