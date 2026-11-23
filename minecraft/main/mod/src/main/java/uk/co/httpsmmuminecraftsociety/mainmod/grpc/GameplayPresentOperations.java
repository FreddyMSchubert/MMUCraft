package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import com.google.protobuf.ByteString;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GameplayPresentOperations {
    private GameplayPresentOperations() {}

    static PresentItemResponse preview(PresentPlayerRequest request) {
        ServerPlayer player = player(request.getMinecraftUsername());
        if (player == null) return itemError("Join the Minecraft server to use the letter.");
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return itemError("Hold the stack you want to send in your main hand.");
        return PresentItemResponse.newBuilder().setSuccess(true)
                .setItemName(stack.getHoverName().getString()).setItemCount(stack.getCount()).build();
    }

    static PresentItemResponse capture(CapturePresentRequest request) throws IOException {
        Data data = data();
        String key = request.getPresentId();
        if (key.isBlank()) return itemError("The letter is invalid.");
        ByteBuffer saved = data.escrow.get(key);
        byte[] bytes;
        ItemStack stack;
        if (saved != null) {
            ByteBuffer copy = saved.duplicate();
            bytes = new byte[copy.remaining()];
            copy.get(bytes);
            stack = decode(GrpcBridge.minecraftServer().registryAccess(), bytes);
        } else {
            if (request.getRecoverOnly()) return itemError("No stack was captured for this letter.");
            ServerPlayer player = player(request.getMinecraftUsername());
            if (player == null) return itemError("Join the Minecraft server to insert a stack.");
            stack = player.getMainHandItem();
            if (stack.isEmpty()) return itemError("Hold a stack in your main hand.");
            bytes = encode(player.registryAccess(), stack);
            data.escrow.put(key, ByteBuffer.wrap(bytes));
            data.setDirty();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return PresentItemResponse.newBuilder().setSuccess(true)
                .setItemData(ByteString.copyFrom(bytes))
                .setItemName(stack.getHoverName().getString()).setItemCount(stack.getCount()).build();
    }

    static DeliverPresentResponse deliver(DeliverPresentRequest request) throws IOException {
        ServerPlayer player = player(request.getMinecraftUsername());
        if (player == null) return deliveryError("Join the Minecraft server to receive this stack.");
        String receipt = player.getUUID() + ":" + request.getReceiptId();
        Data data = data();
        if (data.delivered.contains(receipt)) return DeliverPresentResponse.newBuilder()
                .setAlreadyDelivered(true).setMessage("This stack was already delivered.").build();
        ItemStack stack = decode(player.registryAccess(), request.getItemData().toByteArray());
        if (stack.isEmpty()) return deliveryError("The stored stack is invalid.");
        // An empty slot can hold the whole captured stack. If there is no empty slot,
        // drop the whole stack so a failed drop cannot follow a partial inventory add.
        int freeSlot = player.getInventory().getFreeSlot();
        if (freeSlot < 0) {
            if (DabloonInteractionLog.drop(player, stack) == null)
                return deliveryError("The stack could not be placed nearby. Try again.");
        } else {
            player.getInventory().setItem(freeSlot, stack);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        data.delivered.add(receipt);
        data.setDirty();
        if (!request.getMessage().isBlank()) player.sendSystemMessage(
                Component.literal(request.getMessage()).withStyle(ChatFormatting.RED));
        return DeliverPresentResponse.newBuilder().setDelivered(true)
                .setMessage("The stack was delivered.").build();
    }

    private static byte[] encode(HolderLookup.Provider registries, ItemStack stack) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.store("item", ItemStack.CODEC, RegistryOps.create(NbtOps.INSTANCE, registries), stack.copy());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        // ponytail: 2 MiB cap for a single stack; raise only if real player items exceed it.
        if (out.size() > 2_097_152) throw new IOException("The stack is too large to send.");
        return out.toByteArray();
    }

    private static ItemStack decode(HolderLookup.Provider registries, byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 2_097_152) throw new IOException("Invalid stack size.");
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(2_097_152));
        return tag.read("item", ItemStack.CODEC, RegistryOps.create(NbtOps.INSTANCE, registries))
                .orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer player(String username) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null || username.isBlank()) return null;
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        return player == null || player.hasDisconnected() ? null : player;
    }

    private static Data data() {
        return GrpcBridge.minecraftServer().overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    private static PresentItemResponse itemError(String message) {
        return PresentItemResponse.newBuilder().setMessage(message).build();
    }

    private static DeliverPresentResponse deliveryError(String message) {
        return DeliverPresentResponse.newBuilder().setMessage(message).build();
    }

    private static final class Data extends SavedData {
        private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.BYTE_BUFFER).fieldOf("escrow").forGetter(data -> data.escrow),
                Codec.STRING.listOf().fieldOf("delivered").forGetter(data -> data.delivered.stream().toList())
        ).apply(instance, Data::new));
        private static final SavedDataType<Data> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "christmas_presents"), Data::new, CODEC, null);
        private final Map<String, ByteBuffer> escrow;
        private final Set<String> delivered;

        private Data() { this(Map.of(), java.util.List.of()); }
        private Data(Map<String, ByteBuffer> escrow, java.util.List<String> delivered) {
            this.escrow = new HashMap<>(escrow);
            this.delivered = new HashSet<>(delivered);
        }
    }
}
