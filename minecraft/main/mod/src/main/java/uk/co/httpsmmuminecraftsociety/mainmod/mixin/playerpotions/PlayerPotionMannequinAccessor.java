package uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface PlayerPotionMannequinAccessor {
    @Invoker("setDescription")
    void mainmod$setDescription(Component description);
}
