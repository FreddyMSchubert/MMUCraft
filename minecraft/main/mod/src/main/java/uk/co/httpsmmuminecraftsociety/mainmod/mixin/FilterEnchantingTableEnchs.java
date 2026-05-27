package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.EnchantmentLock;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla.EnchantmentSettingsManager;

import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentMenu.class)
public abstract class FilterEnchantingTableEnchs
{
    @Shadow
    @Final
    private Container enchantSlots;

    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    @Final
    public int[] costs;

    @Shadow
    @Final
    public int[] enchantClue;

    @Shadow
    @Final
    public int[] levelClue;

    @Shadow
    private List<EnchantmentInstance> getEnchantmentList(
            RegistryAccess registryAccess,
            ItemStack stack,
            int slot,
            int level
    ) {
        throw new AssertionError();
    }

    @ModifyArg(
            method = "getEnchantmentList(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/ItemStack;II)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"
            ),
            index = 3
    )
    private Stream<Holder<Enchantment>> mainmod$filterEnchantingTableEnchantments(
            Stream<Holder<Enchantment>> possibleEnchantments
    ) {
        return possibleEnchantments.filter(EnchantmentSettingsManager::isAllowedFromEnchantingTable);
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void mainmod$hideEmptyEnchantingTableOptions(Container container, CallbackInfo ci)
    {
        ItemStack inputStack = this.enchantSlots.getItem(0);

        if (inputStack.isEmpty()) {
            return;
        }

        if (EnchantmentLock.isLocked(inputStack)) {
            boolean changed = clearEnchantingOptions();
            if (changed) {
                ((AbstractContainerMenu) (Object) this).broadcastChanges();
            }
            return;
        }

        this.access.execute((level, blockPos) -> {
            RegistryAccess registryAccess = level.registryAccess();
            boolean changed = false;

            for (int option = 0; option < 3; option++) {
                if (this.costs[option] <= 0) {
                    continue;
                }

                List<EnchantmentInstance> possibleEnchantments =
                        this.getEnchantmentList(
                                registryAccess,
                                inputStack,
                                option,
                                this.costs[option]
                        );

                if (!possibleEnchantments.isEmpty()) {
                    continue;
                }

                this.costs[option] = 0;
                this.enchantClue[option] = -1;
                this.levelClue[option] = -1;
                changed = true;
            }

            if (changed) {
                ((AbstractContainerMenu) (Object) this).broadcastChanges();
            }
        });
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void mainmod$blockLockedItemEnchanting(Player player, int option, CallbackInfoReturnable<Boolean> cir)
    {
        if (EnchantmentLock.isLocked(this.enchantSlots.getItem(0))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void mainmod$lockEnchantmentsAfterEnchanting(Player player, int option, CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValue()) {
            return;
        }

        ItemStack enchantedStack = this.enchantSlots.getItem(0);
        if (!enchantedStack.isEmpty() && EnchantmentHelper.hasAnyEnchantments(enchantedStack)) {
            EnchantmentLock.lock(enchantedStack);
            this.enchantSlots.setChanged();
        }
    }

    private boolean clearEnchantingOptions() {
        boolean changed = false;

        for (int option = 0; option < 3; option++) {
            if (this.costs[option] != 0 || this.enchantClue[option] != -1 || this.levelClue[option] != -1) {
                this.costs[option] = 0;
                this.enchantClue[option] = -1;
                this.levelClue[option] = -1;
                changed = true;
            }
        }

        return changed;
    }
}
