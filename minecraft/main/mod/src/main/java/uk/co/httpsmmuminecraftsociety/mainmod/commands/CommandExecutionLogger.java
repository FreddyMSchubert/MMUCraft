package uk.co.httpsmmuminecraftsociety.mainmod.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.RecordCommandExecutionRequest;

public final class CommandExecutionLogger {
    private static final ThreadLocal<String> DISCORD_ACTOR = new ThreadLocal<>();

    private CommandExecutionLogger() {}

    public static void fromDiscord(String actorName, Runnable command) {
        DISCORD_ACTOR.set(actorName);
        try {
            command.run();
        } finally {
            DISCORD_ACTOR.remove();
        }
    }

    public static CommandResultCallback record(CommandSourceStack source, String rawCommand) {
        String command = rawCommand.strip().replaceFirst("^/+", "");
        if (command.isEmpty()) return CommandResultCallback.EMPTY;

        String discordActor = DISCORD_ACTOR.get();
        ServerPlayer player = source.getPlayer();
        String actorName;
        String minecraftUuid = "";
        String executionSource;
        boolean operator;
        if (discordActor != null) {
            actorName = discordActor;
            executionSource = "discord";
            operator = true;
        } else if (player != null) {
            actorName = player.getName().getString();
            minecraftUuid = player.getUUID().toString();
            executionSource = "minecraft";
            operator = source.getServer().getPlayerList().isOp(player.nameAndId());
        } else {
            actorName = source.getTextName();
            executionSource = "minecraft";
            operator = true;
        }

        return new OutcomeRecorder(command, executionSource, actorName, minecraftUuid, operator);
    }

    public static void finish(CommandResultCallback callback) {
        if (callback instanceof OutcomeRecorder recorder) recorder.finish();
    }

    private static final class OutcomeRecorder implements CommandResultCallback {
        private final String command;
        private final String source;
        private final String actorName;
        private final String minecraftUuid;
        private final boolean operator;
        private boolean succeeded;
        private int result;

        private OutcomeRecorder(
                String command,
                String source,
                String actorName,
                String minecraftUuid,
                boolean operator
        ) {
            this.command = command;
            this.source = source;
            this.actorName = actorName;
            this.minecraftUuid = minecraftUuid;
            this.operator = operator;
        }

        @Override
        public void onResult(boolean success, int value) {
            succeeded |= success;
            if (success) result += value;
        }

        private void finish() {
            RecordCommandExecutionRequest request = RecordCommandExecutionRequest.newBuilder()
                    .setCommand(command)
                    .setSource(source)
                    .setActorName(actorName)
                    .setMinecraftUuid(minecraftUuid)
                    .setIsOperator(operator)
                    .setSucceeded(succeeded)
                    .setResult(result)
                    .setUnixMs(System.currentTimeMillis())
                    .build();
            GameplayGrpcService.recordCommandExecution(request).exceptionally(error -> {
                MainMod.LOGGER.warn("Could not save command execution to the command log", error);
                return null;
            });
        }
    }
}
