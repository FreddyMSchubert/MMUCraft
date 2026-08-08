package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @ModifyArg(
            method = "getDisplayName",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 1
    )
    private Component mainmod$colorPlayerName(Component name) {
        int color = PlayerStatsSync.colorFor((Player) (Object) this);
        return color < 0 ? name : name.copy().withColor(color);
    }
}
