package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.TrailParticles;

@Mixin(ServerPlayer.class)
public abstract class PlayerParticles {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$spawnTrail(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.isSpectator() || !player.isAlive()) return;
        ItemStack item;
        if (player.isFallFlying()) {
            item = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!item.is(Items.ELYTRA)) return;
        } else {
            item = player.getMainHandItem();
            if (!item.is(Items.MACE) || !MaceItem.canSmashAttack(player)
                    || player.onGround() || player.getDeltaMovement().y >= 0) return;
        }
        TrailParticles.spawn(player, ParticleTrailData.getTrailSpec(item), player, true);
    }
}
