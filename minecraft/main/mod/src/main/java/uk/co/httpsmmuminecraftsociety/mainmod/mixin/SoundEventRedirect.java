package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Tuple;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(ServerLevel.class)
public abstract class SoundEventRedirect
{
    private static final List<Tuple<Identifier, Identifier>> SWAPS = List.of(
            // dog music disk
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_fence_gate.close"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.fence_gate.close")
            ),
            // death music disk
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_fence_gate.open"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.fence_gate.open")
            ),
            // droopy likes ricochet music disk
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_pressure_plate.click_off"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.wooden_pressure_plate.click_off")
            ),
            // droopy likes your face music disk
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_pressure_plate.click_on"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.wooden_pressure_plate.click_on")
            ),
            // 9am music disk
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_button.click_off"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.wooden_button.click_off")
            ),
            // obamium pyramid
            new Tuple(
                    Identifier.fromNamespaceAndPath("minecraft", "block.bamboo_wood_button.click_on"),
                    Identifier.fromNamespaceAndPath("minecraft", "block.wooden_button.click_on")
            )
    );
    private static final Map<Identifier, Identifier> MAP = SWAPS.stream()
            .collect(Collectors.toUnmodifiableMap(Tuple::getA, Tuple::getB));

    private static Holder<SoundEvent> remap(Holder<SoundEvent> original, SoundSource source) {
        if (source == SoundSource.RECORDS) {
            return original;
        }

        Identifier fromId = BuiltInRegistries.SOUND_EVENT.getKey(original.value());
        Identifier toId = MAP.get(fromId);
        if (toId == null) {
            return original;
        }

        SoundEvent remapped = BuiltInRegistries.SOUND_EVENT.getValue(toId);
        if (remapped == null) {
            return original;
        }

        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(remapped);
    }

    @ModifyArgs(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;DDDFFJ)V"
            )
    )
    private void mainmod$redirectPositionalSound(Args args) {
        Holder<SoundEvent> sound = args.get(0);
        SoundSource source = args.get(1);
        args.set(0, remap(sound, source));
    }

    @ModifyArgs(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/world/entity/Entity;FFJ)V"
            )
    )
    private void mainmod$redirectEntitySound(Args args) {
        Holder<SoundEvent> sound = args.get(0);
        SoundSource source = args.get(1);
        args.set(0, remap(sound, source));
    }
}
