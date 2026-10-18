package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfInsomniaCharm;

@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomMoveControl")
public class InsomniaPhantomFlightMixin {
    @Shadow(remap = false) @Final private Phantom this$0;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;approach(FFF)F", ordinal = 0), index = 1)
    private float mainmod$increaseSwoopSpeed(float normalSpeed) {
        if (this$0.entityTags().contains(PotionOfInsomniaCharm.BOSS_TAG)) return 2.5F;
        return this$0.entityTags().contains(PotionOfInsomniaCharm.SUMMONED_TAG) ? 2.25F : normalSpeed;
    }
}
