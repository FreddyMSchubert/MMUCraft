package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Mixin(ServerFunctionLibrary.class)
public abstract class BacapMessageFilterMixin {
    private static final String BACAP_REWARDS_NAMESPACE = "bacap_rewards";
    private static final String BACAP_NAMESPACE = "blazeandcave";
    private static final Pattern MESSAGE_COMMAND = Pattern.compile(
            "^(?:.*\\brun\\s+)?(?:tellraw|tell|msg|w|say|teammsg|tm|title)\\b.*$"
    );
    private static final ThreadLocal<Identifier> mainmod$compilingFunction = new ThreadLocal<>();

    @Inject(method = "lambda$reload$3", at = @At("HEAD"))
    private void mainmod$rememberFunction(
            Map.Entry<Identifier, Resource> entry,
            Identifier functionId,
            CommandSourceStack source,
            CallbackInfoReturnable<CommandFunction<CommandSourceStack>> cir
    ) {
        mainmod$compilingFunction.set(functionId);
    }

    @ModifyArg(
            method = "lambda$reload$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;"
            ),
            index = 3
    )
    private List<String> mainmod$removeBacapMessages(List<String> lines) {
        Identifier functionId = mainmod$compilingFunction.get();
        if (functionId == null || !filtersAutomaticMessages(functionId)) {
            return lines;
        }

        return lines.stream()
                .filter(line -> !isMessageCommand(line))
                .toList();
    }

    private static boolean filtersAutomaticMessages(Identifier functionId) {
        return BACAP_REWARDS_NAMESPACE.equals(functionId.getNamespace())
                || (BACAP_NAMESPACE.equals(functionId.getNamespace())
                && functionId.getPath().startsWith("msg/"));
    }

    @Inject(method = "lambda$reload$3", at = @At("RETURN"))
    private void mainmod$forgetFunction(
            Map.Entry<Identifier, Resource> entry,
            Identifier functionId,
            CommandSourceStack source,
            CallbackInfoReturnable<CommandFunction<CommandSourceStack>> cir
    ) {
        mainmod$compilingFunction.remove();
    }

    private static boolean isMessageCommand(String line) {
        String command = line.stripLeading();
        if (command.startsWith("$")) {
            command = command.substring(1).stripLeading();
        }
        return MESSAGE_COMMAND.matcher(command).matches();
    }
}
