package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.AdvancementDropGates;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.FireworkLoadedCrossbowPredicate;
import uk.co.httpsmmuminecraftsociety.mainmod.advancements.MasteryDropGates;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.Set;

@Mixin(PlayerAdvancements.class)
public abstract class MasteryAdvancementVisibilityMixin {
    private static final Identifier MAINMOD$EXPLOSIVE_FIRE = Identifier.parse("blazeandcave:weaponry/explosive_fire");
    @Shadow private ServerPlayer player;
    @Shadow public Set<AdvancementHolder> visible;
    @Shadow private Set<AdvancementHolder> progressChanged;
    @Shadow private Set<AdvancementNode> rootsToUpdate;
    @Shadow public AdvancementTree tree;
    @Shadow public abstract AdvancementProgress getOrStartProgress(AdvancementHolder holder);
    private long mainmod$lastDropRevision = -1;
    private int mainmod$lastDropMask = -1;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void mainmod$lockAwardUntilDrop(AdvancementHolder holder, String criterion,
            CallbackInfoReturnable<Boolean> ci) {
        if (holder.id().equals(MAINMOD$EXPLOSIVE_FIRE)
                && !FireworkLoadedCrossbowPredicate.matches(player.getInventory())) {
            ci.setReturnValue(false);
            return;
        }
        AdvancementNode node = tree.get(holder);
        Identifier rootId = node == null ? holder.id() : node.root().holder().id();
        if (!AdvancementDropGates.isUnlocked(holder.id(), rootId)) ci.setReturnValue(false);
    }

    @Inject(method = "flushDirty", at = @At("HEAD"))
    private void mainmod$refreshMasteryVisibilityOnDropChange(ServerPlayer player, boolean showAdvancements,
            CallbackInfo ci) {
        long revision = FeatureToggles.revision();
        if (mainmod$lastDropRevision == revision) return;
        mainmod$lastDropRevision = revision;
        int mask = MasteryDropGates.enabledMask();
        if (mainmod$lastDropMask == mask) return;
        mainmod$lastDropMask = mask;
        tree.roots().forEach(rootsToUpdate::add);
    }

    @Inject(method = "lambda$updateTreeVisibility$1", at = @At("HEAD"), cancellable = true)
    private void mainmod$revealMasteryMilestonesInOrder(Set<AdvancementNode> additions,
            Set<Identifier> removals, AdvancementNode node, boolean shouldShow, CallbackInfo ci) {
        if (node.holder().id().getNamespace().equals("vanillatweaks")) {
            if (visible.remove(node.holder())) removals.add(node.holder().id());
            ci.cancel();
            return;
        }
        String id = node.holder().id().toString();
        if (!AdvancementDropGates.isUnlocked(node.holder().id(), node.root().holder().id())) {
            if (visible.remove(node.holder())) removals.add(node.holder().id());
            ci.cancel();
            return;
        }
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
                    additions.add(node);
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
