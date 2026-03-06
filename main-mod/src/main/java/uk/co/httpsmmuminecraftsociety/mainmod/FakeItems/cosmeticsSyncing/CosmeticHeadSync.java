package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.cosmeticsSyncing;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public final class CosmeticHeadSync {
    private CosmeticHeadSync() {}

    public static void syncToTracking(ServerPlayer wearer, boolean includeSelf) {
        ClientboundSetEquipmentPacket packet = makeHeadPacket(wearer);

        for (ServerPlayer viewer : PlayerLookup.tracking(wearer)) {
            viewer.connection.send(packet);
        }

        if (includeSelf) {
            wearer.connection.send(packet);
        }
    }

    public static void syncToViewer(ServerPlayer wearer, ServerPlayer viewer) {
        viewer.connection.send(makeHeadPacket(wearer));
    }

    public static ClientboundSetEquipmentPacket makeHeadPacket(ServerPlayer wearer) {
        ItemStack visualHead = buildVisualHead(wearer.getItemBySlot(EquipmentSlot.HEAD));
        return new ClientboundSetEquipmentPacket(
                wearer.getId(),
                List.of(Pair.of(EquipmentSlot.HEAD, visualHead))
        );
    }

    private static ItemStack buildVisualHead(ItemStack realHead) {
        if (realHead.isEmpty()) {
            return ItemStack.EMPTY;
        }

        String assetId = CosmeticsManager.getCosmeticAssetId(realHead);
        if (assetId == null || assetId.isBlank()) {
            return realHead.copy();
        }

        ItemStack fake = new ItemStack(Items.CARVED_PUMPKIN);
        fake.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        List.of(),            // floats
                        List.of(),            // flags
                        List.of(assetId),     // strings[0]
                        List.of()             // colors
                )
        );
        return fake;
    }

    // Event Callbacks

    public static void onStartTracking(Entity trackedEntity, ServerPlayer viewer)
    {
        if (trackedEntity instanceof ServerPlayer wearer) {
            CosmeticHeadSync.syncToViewer(wearer, viewer);
        }
    }

    public static void onJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server)
    {
        ServerPlayer viewer = handler.player;

        for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
            CosmeticHeadSync.syncToViewer(wearer, viewer);
        }
    }
}