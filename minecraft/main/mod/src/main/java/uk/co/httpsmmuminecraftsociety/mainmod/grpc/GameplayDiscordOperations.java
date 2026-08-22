package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
final class GameplayDiscordOperations {
    private GameplayDiscordOperations() {}

    static BroadcastDiscordMessageResponse broadcastDiscordMessageOnMainThread(BroadcastDiscordMessageRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        String name = request.getDiscordName().strip().replaceAll("[\\r\\n]", " ");
        String content = request.getContent().strip().replaceAll("[\\r\\n]+", " ");
        Component message = Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(name + ": ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(content));
        DiscordBridge.broadcastFromDiscord(server, message);
        return BroadcastDiscordMessageResponse.newBuilder().setBroadcast(true).build();
    }

    static RunServerCommandResponse runServerCommandOnMainThread(RunServerCommandRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        List<String> output = new ArrayList<>();
        AtomicBoolean succeeded = new AtomicBoolean();
        AtomicInteger result = new AtomicInteger();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) {
                if (output.stream().mapToInt(String::length).sum() < 16_000) output.add(message.getString());
            }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        String command = request.getCommand().strip().replaceFirst("^/+", "");
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSource(capture).withCallback((success, value) -> {
                    succeeded.set(success);
                    result.set(value);
                }),
                command
        );
        MainMod.LOGGER.info("Discord admin {} ran server command: {}", request.getDiscordUser(), command);
        return RunServerCommandResponse.newBuilder()
                .setSucceeded(succeeded.get())
                .setResult(result.get())
                .setOutput(output.isEmpty() ? "Command returned " + result.get() + "." : String.join("\n", output))
                .build();
    }
}
