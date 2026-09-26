package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerDisguises;
import uk.co.httpsmmuminecraftsociety.mainmod.playerpotions.PlayerPotions;

@Mixin(AreaEffectCloud.class)
abstract class PlayerPotionCloudMixin {
    @Shadow private PotionContents potionContents;
    // ponytail: Cloud targets reset after unload. Persist UUIDs if reloads must not reapply.
    @Unique private final Set<UUID> mainmod$targets = new HashSet<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private void mainmod$cloud(CallbackInfo ci) {
        AreaEffectCloud cloud = (AreaEffectCloud) (Object) this;
        if (cloud.isRemoved() || cloud.isWaiting() || cloud.tickCount % 5 != 0
                || !(cloud.level() instanceof ServerLevel level)) return;
        String identity = PlayerPotions.identity(potionContents);
        if (identity == null) return;
        float radius = cloud.getRadius();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, cloud.getBoundingBox())) {
            double x = player.getX() - cloud.getX();
            double z = player.getZ() - cloud.getZ();
            if (x * x + z * z <= radius * radius && player.isAffectedByPotions()
                    && mainmod$targets.add(player.getUUID())) {
                PlayerDisguises.apply(player, identity, PlayerPotions.duration(identity));
            }
        }
    }
}
