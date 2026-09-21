package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootMenus;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerMixin {
    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void pil$preventSharedLootGeneration(Player player, CallbackInfo ci) {
        if ((Object) this instanceof RandomizableContainerBlockEntity blockEntity && InstancedLootMenus.isLootCandidate(blockEntity)) {
            InstancedLootMenus.preventVanillaBlockUnpack(blockEntity, player);
            ci.cancel();
        }
    }
}
