package dev.freddy.chunklocked.mixin;

import dev.freddy.chunklocked.EventPresentation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @ModifyArg(
            method = "getDisplayName",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 1
    )
    private Component chunklocked$colorPlayerName(Component name) {
        return name.copy().withColor(EventPresentation.colorFor((Player) (Object) this));
    }
}
