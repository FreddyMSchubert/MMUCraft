package uk.co.httpsmmuminecraftsociety.mainmod.mixin.advancementDabloons;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsAccessor {
    @Accessor("isFirstPacket")
    void mainmod$setFirstPacket(boolean firstPacket);

    @Accessor("rootsToUpdate")
    Set<AdvancementNode> mainmod$getRootsToUpdate();
}
