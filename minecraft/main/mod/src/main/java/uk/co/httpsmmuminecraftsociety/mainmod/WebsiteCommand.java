package uk.co.httpsmmuminecraftsociety.mainmod;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;

public final class WebsiteCommand {
    private static final URI WEBSITE_URI = URI.create("https://mmuminecraftsociety.co.uk/");

    private WebsiteCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("website")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(WebsiteCommand::message, false);
                            return 1;
                        })
        );
    }

    private static Component message() {
        return Component.literal("Open the MMU Minecraft Society website: ")
                .append(Component.literal(WEBSITE_URI.toString())
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GOLD)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.OpenUrl(WEBSITE_URI))));
    }

    public static Component takeMeThere(String path) {
        URI uri = WEBSITE_URI.resolve(path);
        return Component.literal("Take me there!")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(uri)));
    }
}
