package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {
    @ModifyArg(
            method = "getDisplayName",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 0
    )
    private Team mainmod$hideOwnerTeamSuffixOnPets(Team team) {
        return (Object) this instanceof TamableAnimal ? null : team;
    }
}
