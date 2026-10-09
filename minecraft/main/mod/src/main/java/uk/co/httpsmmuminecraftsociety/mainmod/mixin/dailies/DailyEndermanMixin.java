package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;

import java.util.function.Predicate;

@Mixin(Enderman.class)
public abstract class DailyEndermanMixin {
    @Redirect(method = "isBeingStaredBy",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private boolean mainmod$cosmeticHeadgearDoesNotDisguiseGaze(Predicate<Object> notDisguised, Object observer) {
        return (observer instanceof Player player
                && CosmeticsManager.determineCosmeticType(player.getItemBySlot(EquipmentSlot.HEAD)).isCosmetic())
                || notDisguised.test(observer);
    }

    @Inject(method = "isBeingStaredBy", at = @At("RETURN"))
    private void mainmod$recordEyeContact(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.EYE_CONTACT_ENDERMAN));
        }
    }
}
