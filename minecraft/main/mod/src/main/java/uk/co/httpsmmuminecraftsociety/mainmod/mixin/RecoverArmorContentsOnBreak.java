package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.util.Prediction;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class RecoverArmorContentsOnBreak {
    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void rememberArmorContents(
            int damage,
            ServerPlayer player,
            Consumer<ItemStack> breakCallback,
            CallbackInfo info,
            @Share("brokenArmor") LocalRef<ItemStack> brokenArmor
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (player != null && equippable != null && equippable.slot().isArmor()
                && player.getItemBySlot(equippable.slot()) == stack) {
            brokenArmor.set(stack.copy());
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void recoverArmorContents(
            int damage,
            ServerPlayer player,
            Consumer<ItemStack> breakCallback,
            CallbackInfo info,
            @Share("brokenArmor") LocalRef<ItemStack> brokenArmor
    ) {
        ItemStack brokenStack = brokenArmor.get();
        if (brokenStack == null || !((ItemStack) (Object) this).isEmpty()) return;

        EquipmentSlot slot = brokenStack.get(DataComponents.EQUIPPABLE).slot();
        ItemStack cosmetic = CosmeticsManager.cosmeticFromHelmetReplica(brokenStack);
        if (!cosmetic.isEmpty()) player.setItemSlot(slot, cosmetic);

        boolean slotIsEmpty = cosmetic.isEmpty();
        for (var storedCharm : CharmorManager.getStoredArmorCharms(brokenStack)) {
            FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
            if (fakeItem == null) continue;

            ItemStack charm = fakeItem.createItemStackAtLevel(storedCharm.level());
            if (slotIsEmpty) {
                player.setItemSlot(slot, charm);
                slotIsEmpty = false;
            } else if (!player.getInventory().add(charm)) {
                player.drop(charm, false, Prediction.SERVER_ONLY);
            }
        }
    }
}
