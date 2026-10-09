package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;

@Mixin(Creaking.class)
abstract class CreakingCosmeticGazeMixin {
    @Redirect(method = "checkCanMove",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private boolean mainmod$cosmeticHeadgearDoesNotDisguiseGaze(Predicate<Object> notDisguised, Object observer) {
        return (observer instanceof Player player
                && CosmeticsManager.determineCosmeticType(player.getItemBySlot(EquipmentSlot.HEAD)).isCosmetic())
                || notDisguised.test(observer);
    }
}
