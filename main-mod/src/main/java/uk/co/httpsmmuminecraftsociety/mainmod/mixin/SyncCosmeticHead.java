package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.cosmeticsSyncing.CosmeticHeadSync;

@Mixin(LivingEntity.class)
public abstract class SyncCosmeticHead {
    @Inject(method = "onEquipItem", at = @At("TAIL"))
    private void mainmod$syncCosmeticHead(
            EquipmentSlot slot,
            ItemStack oldStack,
            ItemStack newStack,
            CallbackInfo ci
    ) {
        if (slot != EquipmentSlot.HEAD) {
            return;
        }

        if ((Object) this instanceof ServerPlayer player) {
            CosmeticHeadSync.syncToTracking(player, true);
        }
    }
}