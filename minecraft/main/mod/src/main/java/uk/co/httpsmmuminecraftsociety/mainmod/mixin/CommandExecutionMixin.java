package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.commands.CommandExecutionLogger;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(Commands.class)
public final class CommandExecutionMixin {
    private static final ThreadLocal<Deque<CommandResultCallback>> mainmod$commandOutcomes =
            ThreadLocal.withInitial(ArrayDeque::new);

    @ModifyVariable(method = "performCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ParseResults<CommandSourceStack> mainmod$attachCommandOutcome(
            ParseResults<CommandSourceStack> parseResults,
            ParseResults<CommandSourceStack> originalParseResults,
            String command
    ) {
        CommandResultCallback outcome = CommandExecutionLogger.record(
                parseResults.getContext().getSource(),
                command
        );
        mainmod$commandOutcomes.get().push(outcome);
        return Commands.mapSource(parseResults, source ->
                source.withCallback(outcome, CommandResultCallback::chain)
        );
    }

    @Inject(method = "performCommand", at = @At("RETURN"))
    private void mainmod$finishCommandOutcome(
            ParseResults<CommandSourceStack> parseResults,
            String command,
            CallbackInfo callback
    ) {
        Deque<CommandResultCallback> outcomes = mainmod$commandOutcomes.get();
        CommandExecutionLogger.finish(outcomes.pop());
        if (outcomes.isEmpty()) mainmod$commandOutcomes.remove();
    }
}
