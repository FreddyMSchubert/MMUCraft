package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(PlayerAdvancements.class)
public abstract class MasteryAdvancementVisibilityMixin {
    @Shadow private ServerPlayer player;
    @Shadow public Set<AdvancementHolder> visible;
    @Shadow private Set<AdvancementHolder> progressChanged;
    @Shadow public abstract AdvancementProgress getOrStartProgress(AdvancementHolder holder);

    @Inject(method = "lambda$updateTreeVisibility$1", at = @At("HEAD"), cancellable = true)
    private void mainmod$revealMasteryMilestonesInOrder(Set<AdvancementHolder> additions,
            Set<Identifier> removals, AdvancementNode node, boolean shouldShow, CallbackInfo ci) {
        String id = node.holder().id().toString();
        if (!id.startsWith("mainmod:mastery/")) return;
        boolean numbered = Character.isDigit(id.charAt(id.length() - 1))
                && id.lastIndexOf('_') > id.lastIndexOf('/');
        boolean hour = (numbered && id.startsWith("mainmod:mastery/social/join_"))
                || id.equals("mainmod:mastery/social/unreasonable_hours");
        if (!hour && !numbered) return;

        AdvancementHolder prerequisite = id.equals("mainmod:mastery/sniffers/bred_10000")
                ? player.level().getServer().getAdvancements().get(Identifier.parse("mainmod:mastery/sniffers/bred_1000"))
                : hour
                        ? player.level().getServer().getAdvancements().get(Identifier.parse("mainmod:mastery/social/join_insane_hour"))
                        : node.parent().holder();
        if (prerequisite != null && getOrStartProgress(prerequisite).isDone()) {
            if (hour) {
                if (visible.add(node.holder())) {
                    additions.add(node.holder());
                    if (getOrStartProgress(node.holder()).isDone()) progressChanged.add(node.holder());
                }
                ci.cancel();
            }
            return;
        }
        if (visible.remove(node.holder())) removals.add(node.holder().id());
        ci.cancel();
    }
}
