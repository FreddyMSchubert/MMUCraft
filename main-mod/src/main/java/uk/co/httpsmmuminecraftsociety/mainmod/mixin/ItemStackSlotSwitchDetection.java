package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmsManager;

@Mixin(ItemStack.class)
public class ItemStackSlotSwitchDetection
{
    @Unique private int lastSlot = Integer.MIN_VALUE;

    public int getLastSlot() { return lastSlot; }
    public void setLastSlot(int slot) { lastSlot = slot; }

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void detectSlotMove(Level level, Entity entity, EquipmentSlot equipmentSlot, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        final ItemStack self = (ItemStack)(Object)this;
        if (self.isEmpty()) return;
        if (equipmentSlot == null) return;

        int currentSlot = equipmentSlot.ordinal();

        boolean firstSeen = (lastSlot == Integer.MIN_VALUE);

        if (!firstSeen && currentSlot != lastSlot) {
            CharmsManager.onItemStackSlotChange(player, self, lastSlot, currentSlot);
        }

        lastSlot = currentSlot;
    }
}
