package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Predicate;

@Mixin(CommandNode.class)
public interface CommandNodeAccessor<S> {
    @Accessor("requirement")
    @Final
    @Mutable
    void mainmod$setRequirement(Predicate<S> requirement);

    @Accessor("redirect")
    @Final
    @Mutable
    void mainmod$setRedirect(CommandNode<S> redirect);
}
