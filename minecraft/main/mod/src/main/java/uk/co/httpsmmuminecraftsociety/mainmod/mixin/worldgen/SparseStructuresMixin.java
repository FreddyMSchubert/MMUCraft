package uk.co.httpsmmuminecraftsociety.mainmod.mixin.worldgen;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.worldgen.SparseStructures;

@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public class SparseStructuresMixin {
    @Inject(
            method = "loadFromResource",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private static <T> void mainmod$makeNonVanillaStructuresSparse(
            Decoder<T> elementDecoder,
            RegistryOps<JsonElement> ops,
            ResourceKey<T> elementKey,
            Resource resource,
            CallbackInfoReturnable<Either<T, Exception>> cir,
            @Local(name = "json") JsonElement json
    ) {
        SparseStructures.apply(elementKey, json);
    }
}
