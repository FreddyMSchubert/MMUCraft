package uk.co.httpsmmuminecraftsociety.mainmod.mixin.anvilRework;

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.AnvilLogic;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;

import java.util.Objects;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow private int repairItemCountCost;
    @Shadow @Nullable private String itemName;
    @Shadow @Final private DataSlot cost;
    @Shadow private boolean onlyRenaming;

    @Unique
    private AnvilLogic.Outcome mainmod$lastOutcome = AnvilLogic.Outcome.EMPTY;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mainmod$replaceCreateResult(CallbackInfo ci) {
        ItemCombinerMenuAccessor combiner = (ItemCombinerMenuAccessor)this;
        Container inputSlots = combiner.mainmod$getInputSlots();
        ResultContainer resultSlots = combiner.mainmod$getResultSlots();
        Player menuPlayer = combiner.mainmod$getPlayer();

        if (!(menuPlayer instanceof ServerPlayer serverPlayer)) {
            this.mainmod$lastOutcome = AnvilLogic.Outcome.EMPTY;
            this.onlyRenaming = false;
            this.repairItemCountCost = 0;
            this.cost.set(0);
            resultSlots.setItem(0, ItemStack.EMPTY);
            ((AbstractContainerMenuInvoker)this).mainmod$broadcastChanges();
            ci.cancel();
            return;
        }

        ItemStack left = inputSlots.getItem(0).copy();
        ItemStack right = inputSlots.getItem(1).copy();

        this.mainmod$lastOutcome = AnvilLogic.compute(serverPlayer, left, right, this.itemName);
        if (this.mainmod$lastOutcome == null) {
            this.mainmod$lastOutcome = AnvilLogic.Outcome.EMPTY;
        }

        this.onlyRenaming = false;
        this.repairItemCountCost = 0;
        this.cost.set(clampCost(this.mainmod$lastOutcome.xpLevelsConsumed()));
        resultSlots.setItem(0, this.mainmod$lastOutcome.result().copy());

        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker)this;
        menu.mainmod$broadcastChanges();
        menu.mainmod$broadcastFullState();
        menu.mainmod$sendAllDataToRemote();
        mainmod$syncXp(menuPlayer);

        ci.cancel();
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mainmod$replaceMayPickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
        ItemCombinerMenuAccessor combiner = (ItemCombinerMenuAccessor)this;
        ResultContainer resultSlots = combiner.mainmod$getResultSlots();

        ItemStack result = resultSlots.getItem(0);
        boolean allow =
                hasItem
                        && !result.isEmpty()
                        && (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get());

        cir.setReturnValue(allow);
    }

    @Unique
    private static void mainmod$syncXp(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(
                    new ClientboundSetExperiencePacket(
                            serverPlayer.experienceProgress,
                            serverPlayer.totalExperience,
                            serverPlayer.experienceLevel
                    )
            );
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void mainmod$replaceOnTake(Player player, ItemStack carried, CallbackInfo ci) {
        ItemCombinerMenuAccessor combiner = (ItemCombinerMenuAccessor)this;
        Container inputSlots = combiner.mainmod$getInputSlots();
        ResultContainer resultSlots = combiner.mainmod$getResultSlots();
        ContainerLevelAccess access = combiner.mainmod$getAccess();

        ItemStack original = inputSlots.getItem(0);
        if (player instanceof ServerPlayer serverPlayer
                && original.get(DataComponents.TOOL) != null
                && carried.get(DataComponents.CUSTOM_NAME) != null
                && !Objects.equals(original.get(DataComponents.CUSTOM_NAME), carried.get(DataComponents.CUSTOM_NAME))) {
            DailyTaskManager.record(serverPlayer, DailyTaskEvent.simple(DailySimpleEvent.RENAME_TOOL));
        }

        int chargedCost = clampCost(this.mainmod$lastOutcome.xpLevelsConsumed());
        if (!player.hasInfiniteMaterials() && chargedCost > 0) {
            player.giveExperienceLevels(-chargedCost);
        }

        inputSlots.setItem(0, this.mainmod$lastOutcome.leftRemainder().copy());
        inputSlots.setItem(1, this.mainmod$lastOutcome.rightRemainder().copy());
        resultSlots.setItem(0, ItemStack.EMPTY);

        this.onlyRenaming = false;
        this.repairItemCountCost = 0;
        this.cost.set(0);

        inputSlots.setChanged();
        resultSlots.setChanged();

        access.execute((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!player.hasInfiniteMaterials() && state.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState newBlockState = AnvilBlock.damage(state);
                if (newBlockState == null) {
                    level.removeBlock(pos, false);
                    level.levelEvent(1029, pos, 0);
                } else {
                    level.setBlock(pos, newBlockState, 2);
                    level.levelEvent(1030, pos, 0);
                }
            } else {
                level.levelEvent(1030, pos, 0);
            }
        });

        ((AnvilMenu)(Object)this).createResult();

        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker)this;
        menu.mainmod$broadcastChanges();
        menu.mainmod$broadcastFullState();
        menu.mainmod$sendAllDataToRemote();
        mainmod$syncXp(player);

        ci.cancel();
    }

    @Unique
    private static int clampCost(int cost) {
        if (cost < 0) return 0;
        return Math.min(cost, 39);
    }
}
