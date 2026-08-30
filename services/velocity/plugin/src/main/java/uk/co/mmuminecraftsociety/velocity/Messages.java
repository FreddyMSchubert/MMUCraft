package uk.co.mmuminecraftsociety.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class Messages {
    private static final String DISCORD_URL = "https://discord.gg/pPgZqRS5b2";
    private static final String DISCORD_DISPLAY_URL = "discord.gg/pPgZqRS5b2";
    private static final TextColor GOLD = TextColor.color(0xFFD166);
    private static final TextColor RED = TextColor.color(0xFF6B6B);
    private static final TextColor BLUE = TextColor.color(0xA6DEFF);
    private static final TextColor TEXT = TextColor.color(0xE8EDF2);
    private static final TextColor MUTED = TextColor.color(0xAAB4BE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z");

    private Messages() { }

    static Component access(ApiClient.AccessDecision decision, String username) {
        if (decision.status() == null) return authenticationUnavailable();
        return switch (decision.status()) {
            case "SIGNUP_REQUIRED" -> signupRequired(username, decision.websiteUrl());
            case "SIGNUP_CODE" -> decision.code() == null || decision.code().isBlank()
                    ? authenticationUnavailable()
                    : signupCode(decision.code());
            case "BANNED" -> restriction("This ban is permanent.");
            case "TIMEOUT" -> timeout(decision.expiresAtUnixMs());
            case "MAINTENANCE" -> maintenance();
            case "DENIED" -> denied();
            default -> authenticationUnavailable();
        };
    }

    static Component maintenance() {
        return heading("Maintenance in progress", GOLD)
                .append(Component.text("The network is temporarily unavailable while we work on it.\n", TEXT))
                .append(Component.text("Please try again later.", MUTED));
    }

    static Component unavailable() {
        return heading("Server unavailable", RED)
                .append(Component.text("We could not connect you to the selected Minecraft server.\n", TEXT))
                .append(Component.text("Please try again in a moment.\n\n", MUTED))
                .append(discordHelp("If this continues, tell the committee on "));
    }

    static Component transferUnavailable() {
        return heading("Transfer failed", RED)
                .append(Component.text("That server is not available right now. You are still connected here.", TEXT));
    }

    static Component disconnected(Component reason) {
        Component message = heading("Connection closed", RED)
                .append(Component.text("The Minecraft server ended your connection.", TEXT));
        if (reason != null) {
            message = message.append(Component.text("\n\nReason: ", MUTED)).append(reason);
        }
        return message.append(Component.text("\n\n")).append(discordHelp("If this looks wrong, tell the committee on "));
    }

    static Component authenticationUnavailable() {
        return heading("Login service unavailable", RED)
                .append(Component.text("We could not verify your account right now.\n", TEXT))
                .append(Component.text("Please try again in a moment.\n\n", MUTED))
                .append(discordHelp("If this continues, tell the committee on "));
    }

    static Component restriction(String status, Long expiresAtUnixMs) {
        if (status == null) return denied();
        return switch (status) {
            case "BANNED" -> restriction("This ban is permanent.");
            case "TIMEOUT" -> timeout(expiresAtUnixMs);
            default -> denied();
        };
    }

    private static Component signupRequired(String username, String websiteUrl) {
        String url = websiteUrl == null || websiteUrl.isBlank()
                ? "https://mmuminecraftsociety.co.uk"
                : websiteUrl;
        return heading("Welcome, " + username + "!", GOLD)
                .append(Component.text("This network checks every player to keep hackers, demons and the ", TEXT))
                .append(Component.text("chupacabra").decorate(TextDecoration.OBFUSCATED))
                .append(Component.text(" at bay.\n\n", TEXT))
                .append(Component.text("Your Minecraft account is not linked yet.\n", RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("Do not worry. This is normal.\n\n", MUTED))
                .append(Component.text("Finish signup here:\n", TEXT))
                .append(Component.text(url, BLUE)
                        .clickEvent(ClickEvent.openUrl(url)))
                .append(Component.text("\n\nAfter verification, join again.", MUTED));
    }

    private static Component signupCode(String code) {
        String displayCode = code.replace("|", " → ");
        return heading("Almost finished", GOLD)
                .append(Component.text("Your signup code is:\n", TEXT))
                .append(Component.text(displayCode, GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("\n\nChoose these three items on the website. :D", MUTED));
    }

    private static Component denied() {
        return heading("Login denied", RED)
                .append(Component.text("We could not accept these Minecraft account details.\n\n", TEXT))
                .append(discordHelp("If this looks wrong, tell the committee on "));
    }

    private static Component timeout(Long unixMs) {
        return restriction(unixMs == null
                ? "This timeout is still active."
                : "Your timeout continues until " + formatDate(unixMs) + ".");
    }

    private static Component restriction(String duration) {
        return heading("Access restricted", RED)
                .append(Component.text(duration, TEXT))
                .append(Component.text("\n\n"))
                .append(discordHelp("If this looks wrong, contact the committee on "));
    }

    private static Component heading(String title, TextColor color) {
        return Component.empty()
                .append(Component.text("MMU MINECRAFT SOCIETY", GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text(title, color).decorate(TextDecoration.BOLD))
                .append(Component.text("\n\n"));
    }

    private static Component discordHelp(String introduction) {
        return Component.text(introduction, MUTED)
                .append(Component.text("Discord: ", BLUE))
                .append(Component.text(DISCORD_DISPLAY_URL, BLUE)
                        .clickEvent(ClickEvent.openUrl(DISCORD_URL)))
                .append(Component.text(".", MUTED));
    }

    private static String formatDate(Long unixMs) {
        if (unixMs == null) return "an unknown time";
        return DATE.format(Instant.ofEpochMilli(unixMs).atZone(ZoneId.systemDefault()));
    }
}
