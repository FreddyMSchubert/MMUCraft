package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;

final class UnlockBookAnimation {
    private static final byte TOTEM_ANIMATION_EVENT = 35;

    private UnlockBookAnimation() {
    }

    static void play(ServerPlayer player, ItemStack displayedItem) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack serverItem = player.getInventory().getSelectedItem().copy();
        ItemStack animationItem = displayedItem.copy();
        animationItem.setCount(1);
        animationItem.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);

        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, animationItem));
        player.connection.send(new ClientboundEntityEventPacket(player, TOTEM_ANIMATION_EVENT));
        player.connection.send(new ClientboundSetPlayerInventoryPacket(selectedSlot, serverItem));
    }
}
