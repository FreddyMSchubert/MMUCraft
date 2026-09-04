package uk.co.httpsmmuminecraftsociety.mainmod.mixin.particleTrails;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.ParticleTrailData;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails.TrailParticles;

@Mixin(ServerPlayer.class)
public abstract class ElytraParticles {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$spawnTrail(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!player.isFallFlying() || player.isSpectator() || !player.isAlive()) return;
        ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
        if (elytra.is(Items.ELYTRA)) TrailParticles.spawn(player, ParticleTrailData.getTrailSpec(elytra), player, true);
    }
}
