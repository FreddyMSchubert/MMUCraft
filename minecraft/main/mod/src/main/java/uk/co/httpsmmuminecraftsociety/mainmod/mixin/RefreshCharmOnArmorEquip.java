package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.List;

@Mixin(LivingEntity.class)
public class RefreshCharmOnArmorEquip
{
    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void refreshCharm(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo info) {
        if (slot.isArmor()) CharmsManager.refresh(newStack);
    }

    @Inject(method = "onEquippedItemBroken", at = @At("RETURN"))
    private void recoverArmorCharms(Item item, EquipmentSlot slot, CallbackInfo info) {
        if (!slot.isArmor() || !((Object) this instanceof ServerPlayer player)) return;

        List<StoredCharmData> storedCharms = CharmorManager.getStoredArmorCharms(player.getItemBySlot(slot));
        boolean slotIsEmpty = true;
        for (StoredCharmData storedCharm : storedCharms) {
            FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
            if (fakeItem == null) continue;

            ItemStack charm = fakeItem.createItemStackAtLevel(storedCharm.level());
            if (slotIsEmpty) {
                player.setItemSlot(slot, charm);
                slotIsEmpty = false;
            } else if (!player.getInventory().add(charm)) {
                player.drop(charm, false);
            }
        }
    }
}
