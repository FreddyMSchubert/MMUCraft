package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

@Mixin(AbstractContainerMenu.class)
public abstract class DabloonContainerClickMixin {
    @Unique private int mainmod$balanceBefore;
    @Unique private String[] mainmod$slotsBefore = {};
    @Unique private String[] mainmod$kindsBefore = {};
    @Unique private int[] mainmod$valuesBefore = {};
    @Unique private String mainmod$carriedBefore = "";
    @Unique private String mainmod$carriedKindBefore = "";
    @Unique private int mainmod$carriedValueBefore;
    @Unique private String mainmod$itemIdBefore = "";
    @Unique private int mainmod$itemCountBefore;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void mainmod$beforeCurrencyClick(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        mainmod$balanceBefore = MoneyHelper.GetBalance(serverPlayer);
        mainmod$slotsBefore = new String[menu.slots.size()];
        mainmod$kindsBefore = new String[menu.slots.size()];
        mainmod$valuesBefore = new int[menu.slots.size()];
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            mainmod$kindsBefore[i] = DabloonInteractionLog.kind(stack);
            mainmod$slotsBefore[i] = mainmod$kindsBefore[i].isEmpty() ? "" : DabloonInteractionLog.describe(stack);
            mainmod$valuesBefore[i] = mainmod$kindsBefore[i].isEmpty() ? 0 : DabloonInteractionLog.value(stack);
        }
        ItemStack carried = menu.getCarried();
        mainmod$carriedKindBefore = DabloonInteractionLog.kind(carried);
        mainmod$carriedBefore = mainmod$carriedKindBefore.isEmpty() ? "" : DabloonInteractionLog.describe(carried);
        mainmod$carriedValueBefore = mainmod$carriedKindBefore.isEmpty() ? 0 : DabloonInteractionLog.value(carried);
        ItemStack target = slot(menu, slotId);
        ItemStack subject = DabloonInteractionLog.kind(target).isEmpty() ? carried : target;
        String subjectKind = DabloonInteractionLog.kind(subject);
        mainmod$itemIdBefore = subjectKind.isEmpty() ? "" : DabloonInteractionLog.itemId(subject);
        mainmod$itemCountBefore = subjectKind.isEmpty() ? 0 : subject.getCount();
        if (button == 1 && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)
                && !subjectKind.isEmpty()) {
            DabloonInteractionLog.record(serverPlayer, "right_click", subjectKind,
                    mainmod$itemIdBefore, mainmod$itemCountBefore, DabloonInteractionLog.value(subject),
                    mainmod$balanceBefore, mainmod$balanceBefore,
                    "Menu=" + menu.getClass().getSimpleName() + "; click=" + clickType + "; slot=" + slotId
                            + " (" + mainmod$slotLabel(menu, slotId) + ")"
                            + "; target=" + DabloonInteractionLog.describe(target) + "; carried=" + mainmod$carriedBefore);
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void mainmod$afterCurrencyClick(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        int after = MoneyHelper.GetBalance(serverPlayer);
        StringBuilder changes = new StringBuilder();
        long increase = 0;
        long decrease = 0;
        String kinds = "";
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            String currentKind = DabloonInteractionLog.kind(stack);
            String current = currentKind.isEmpty() ? "" : DabloonInteractionLog.describe(stack);
            String previous = mainmod$slotsBefore[i];
            if (previous.equals(current)) continue;
            int currentValue = currentKind.isEmpty() ? 0 : DabloonInteractionLog.value(stack);
            increase += Math.max(0, currentValue - mainmod$valuesBefore[i]);
            decrease += Math.max(0, mainmod$valuesBefore[i] - currentValue);
            kinds += mainmod$kindsBefore[i] + currentKind;
            if (changes.length() < 7500) changes.append("; slot ").append(i)
                    .append(" (").append(mainmod$slotLabel(menu, i)).append(") ")
                    .append(previous.isEmpty() ? "empty" : previous).append(" -> ")
                    .append(current.isEmpty() ? "empty" : current);
        }
        ItemStack carried = menu.getCarried();
        String carriedKindAfter = DabloonInteractionLog.kind(carried);
        String carriedAfter = carriedKindAfter.isEmpty() ? "" : DabloonInteractionLog.describe(carried);
        if (!mainmod$carriedBefore.equals(carriedAfter)) {
            int currentValue = carriedKindAfter.isEmpty() ? 0 : DabloonInteractionLog.value(carried);
            increase += Math.max(0, currentValue - mainmod$carriedValueBefore);
            decrease += Math.max(0, mainmod$carriedValueBefore - currentValue);
            kinds += mainmod$carriedKindBefore + carriedKindAfter;
            if (changes.length() < 7500) changes.append("; carried ")
                    .append(mainmod$carriedBefore.isEmpty() ? "empty" : mainmod$carriedBefore).append(" -> ")
                    .append(carriedAfter.isEmpty() ? "empty" : carriedAfter);
        }
        if (changes.isEmpty() && after == mainmod$balanceBefore) return;
        if (kinds.isEmpty() && after == mainmod$balanceBefore) return;
        String kind = kinds.contains("container") ? "container"
                : kinds.contains("coin") && kinds.contains("wallet") ? "mixed"
                : kinds.contains("coin") ? "coin" : kinds.contains("wallet") ? "wallet" : "balance";
        long moved = increase > 0 && decrease > 0 ? Math.min(increase, decrease) : Math.max(increase, decrease);
        int amount = (int)Math.min(Integer.MAX_VALUE, Math.max(moved, Math.abs((long)after - mainmod$balanceBefore)));
        DabloonInteractionLog.record(serverPlayer, "transfer", kind, mainmod$itemIdBefore, mainmod$itemCountBefore,
                amount, mainmod$balanceBefore, after,
                "Menu=" + menu.getClass().getSimpleName() + "; click=" + clickType + "; slot=" + slotId
                        + "; button=" + button + changes);
    }

    @Unique
    private static ItemStack slot(AbstractContainerMenu menu, int slotId) {
        return slotId >= 0 && slotId < menu.slots.size() ? menu.getSlot(slotId).getItem() : ItemStack.EMPTY;
    }

    @Unique
    private static String mainmod$slotLabel(AbstractContainerMenu menu, int slotId) {
        if (slotId < 0 || slotId >= menu.slots.size()) return "outside menu";
        Container container = menu.getSlot(slotId).container;
        if (container instanceof Inventory) return "player inventory";
        if (container instanceof BlockEntity block) {
            return BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock())
                    + " at " + block.getBlockPos().toShortString();
        }
        return container.getClass().getSimpleName();
    }
}
