package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.MobEffect$AttributeTemplate")
public abstract class LuckMobEffectMixin {
    @Shadow @Final private Identifier id;

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void mainmod$setLuckAmount(int amplifier, CallbackInfoReturnable<AttributeModifier> cir) {
        if (this.id.equals(Identifier.withDefaultNamespace("effect.luck"))) {
            cir.setReturnValue(new AttributeModifier(
                    this.id,
                    3.0D * (amplifier + 1),
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }
}
