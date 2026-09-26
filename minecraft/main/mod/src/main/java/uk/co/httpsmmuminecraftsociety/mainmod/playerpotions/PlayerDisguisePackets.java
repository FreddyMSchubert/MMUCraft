package uk.co.httpsmmuminecraftsociety.mainmod.playerpotions;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class PlayerDisguisePackets {
    private PlayerDisguisePackets() {}

    public static Packet<?> forObserver(ServerPlayer observer, Packet<?> packet) {
        if (!PlayerDisguises.anyActive()) return packet;
        if (packet instanceof ClientboundBundlePacket bundle) {
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            boolean changedAny = false;
            for (Packet<? super ClientGamePacketListener> child : bundle.subPackets()) {
                @SuppressWarnings("unchecked")
                Packet<? super ClientGamePacketListener> changed =
                        (Packet<? super ClientGamePacketListener>) forObserver(observer, child);
                changedAny |= changed != child;
                packets.add(changed);
            }
            return changedAny ? new ClientboundBundlePacket(packets) : packet;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipment
                && observer.level() instanceof ServerLevel level
                && level.getEntity(equipment.getEntity()) instanceof ServerPlayer wearer
                && wearer != observer && PlayerDisguises.active(wearer)) {
            var hidden = new ArrayList<Pair<EquipmentSlot, ItemStack>>();
            for (var slot : equipment.getSlots()) hidden.add(Pair.of(slot.getFirst(), ItemStack.EMPTY));
            return new ClientboundSetEquipmentPacket(equipment.getEntity(), hidden);
        }
        if (packet instanceof ClientboundSetEntityDataPacket data
                && data.id() == observer.getId() && PlayerDisguises.active(observer)) {
            var values = new ArrayList<SynchedEntityData.DataValue<?>>(data.packedItems());
            for (int i = 0; i < values.size(); i++) {
                var value = values.get(i);
                if (value.id() == 0 && value.value() instanceof Byte flags) {
                    values.set(i, new SynchedEntityData.DataValue<>(0,
                            EntityDataSerializers.BYTE, (byte) (flags & ~0x20)));
                    return new ClientboundSetEntityDataPacket(data.id(), values);
                }
            }
        }
        return packet;
    }
}
