package uk.co.mmuminecraftsociety.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class Messages {
    private static final String DISCORD_URL = "https://discord.gg/pPgZqRS5b2";
    private static final String SUPPORT_EMAIL = "mmuminecraftsociety@gmail.com";
    private static final TextColor GOLD = TextColor.color(0xFFD166);
    private static final TextColor RED = TextColor.color(0xFF6B6B);
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
            case "BANNED" -> banned();
            case "TIMEOUT" -> timeout(decision.expiresAtUnixMs());
            case "MAINTENANCE" -> maintenance();
            case "DENIED" -> denied();
            default -> authenticationUnavailable();
        };
    }

    static Component maintenance() {
        return heading("Maintenance in progress", GOLD)
                .append(Component.text("The server is temporarily unavailable while we work on it.\n", TEXT))
                .append(Component.text("Please try again later.", MUTED));
    }

    static Component updating(long startedAt, boolean mainOnline) {
        long elapsed = Math.max(0, Instant.now().getEpochSecond() - startedAt);
        if (elapsed > 600) {
            return heading("Server update delayed", RED)
                    .append(Component.text("The update started " + formatDuration(elapsed) + " ago (more than 10 minutes).\n", TEXT))
                    .append(Component.text(mainOnline
                            ? "The main server responds, but deployment checks have not finished.\n\n"
                            : "The main Minecraft server is still not reachable.\n\n", TEXT))
                    .append(support("Please contact the committee"));
        }
        return heading("Server update in progress", GOLD)
                .append(Component.text("The update started " + formatDuration(elapsed) + " ago.\n", TEXT))
                .append(Component.text("Please allow about 3 minutes 20 seconds to 5 minutes in total.\n", TEXT))
                .append(Component.text("If it takes more than 10 minutes, please contact the committee.\n", TEXT))
                .append(Component.text("Join again in a few minutes. Thank you for waiting!", MUTED));
    }

    static Component updateStateUnavailable() {
        return heading("Update status unavailable", RED)
                .append(Component.text("We cannot read the server update status.\n\n", TEXT))
                .append(support("Please contact the committee"));
    }

    static Component unavailable() {
        return heading("Server unavailable", RED)
                .append(Component.text("We could not connect you to the selected Minecraft server.\n", TEXT))
                .append(Component.text("Please try again in a moment.\n\n", MUTED))
                .append(support("If this continues, tell the committee"));
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
        return message.append(Component.text("\n\n")).append(support("If this looks wrong, tell the committee"));
    }

    static Component authenticationUnavailable() {
        return heading("Login service unavailable", RED)
                .append(Component.text("We could not verify your account right now.\n", TEXT))
                .append(Component.text("Please try again in a moment.\n\n", MUTED))
                .append(support("If this continues, tell the committee"));
    }

    static Component restriction(String status, Long expiresAtUnixMs) {
        if (status == null) return denied();
        return switch (status) {
            case "BANNED" -> banned();
            case "TIMEOUT" -> timeout(expiresAtUnixMs);
            default -> denied();
        };
    }

    private static Component signupRequired(String username, String websiteUrl) {
        String url = websiteUrl == null || websiteUrl.isBlank()
                ? "https://mmuminecraftsociety.co.uk"
                : websiteUrl;
        return heading("Welcome, " + username + "!", GOLD)
                .append(Component.text("Before you can play, we need to keep hackers, demons and the ", TEXT))
                .append(Component.text("chupacabra").decorate(TextDecoration.OBFUSCATED))
                .append(Component.text(" at bay.\n\n", TEXT))
                .append(Component.text("Your Minecraft account is not linked yet.\n", RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("Do not worry. This is normal.\n\n", MUTED))
                .append(Component.text("Finish signup here:\n", TEXT))
                .append(Component.text(url))
                .append(Component.text("\n\nWhen the website tells you to log in to the Minecraft server while you sign up, "
                        + "go back to the server list and join the server again.", MUTED));
    }

    private static Component signupCode(String code) {
        String displayCode = code.replace("|", " → ");
        return heading("Almost finished", GOLD)
                .append(Component.text("Choose these items on the website:\n", TEXT))
                .append(Component.text(displayCode, GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("\n\nThen continue signup on the website. :D", MUTED));
    }

    private static Component denied() {
        return heading("Login denied", RED)
                .append(Component.text("We could not accept these Minecraft account details.\n\n", TEXT))
                .append(support("If this looks wrong, tell the committee"));
    }

    private static Component banned() {
        return restriction("You have been banned. This is permanent.",
                "If this looks wrong, contact the committee");
    }

    private static Component timeout(Long unixMs) {
        return unixMs == null
                ? restriction("You have been timed out, but the server cannot determine when it ends. Something is wrong.",
                        "Please contact the committee immediately")
                : restriction("You have been timed out. This lasts until " + formatDate(unixMs) + ".",
                        "If this looks wrong, contact the committee");
    }

    private static Component restriction(String explanation, String supportIntroduction) {
        return heading("Access restricted", RED)
                .append(Component.text(explanation, TEXT))
                .append(Component.text("\n\n"))
                .append(support(supportIntroduction));
    }

    private static Component heading(String title, TextColor color) {
        return Component.empty()
                .append(Component.text("MMU MINECRAFT SOCIETY", GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text(title, color).decorate(TextDecoration.BOLD))
                .append(Component.text("\n\n"));
    }

    private static Component support(String introduction) {
        return Component.text(introduction + ":\nDiscord: ", MUTED)
                .append(Component.text(DISCORD_URL))
                .append(Component.text("\nEmail: ", MUTED))
                .append(Component.text(SUPPORT_EMAIL));
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + (minutes == 1 ? " minute " : " minutes ")
                + seconds + (seconds == 1 ? " second" : " seconds");
    }

    private static String formatDate(Long unixMs) {
        if (unixMs == null) return "an unknown time";
        return DATE.format(Instant.ofEpochMilli(unixMs).atZone(ZoneId.systemDefault()));
    }
}
