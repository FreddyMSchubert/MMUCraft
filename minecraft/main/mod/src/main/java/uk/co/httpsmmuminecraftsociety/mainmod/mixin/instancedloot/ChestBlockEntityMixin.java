package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootData;
import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootNbt;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void pil$loadInstancedLoot(ValueInput input, CallbackInfo ci) {
        ChestBlockEntity chest = (ChestBlockEntity) (Object) this;
        InstancedLootNbt.load(input, (InstancedLootData) this, chest.getContainerSize());
        if (((InstancedLootData) this).pil$isInstancedLoot()) {
            chest.setLootTable(null);
            chest.setLootTableSeed(0L);
            chest.clearContent();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void pil$saveInstancedLoot(ValueOutput output, CallbackInfo ci) {
        InstancedLootNbt.save(output, (InstancedLootData) this);
    }
}
