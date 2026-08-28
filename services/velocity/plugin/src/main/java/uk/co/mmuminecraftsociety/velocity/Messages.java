package uk.co.mmuminecraftsociety.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class Messages {
    private static final TextColor GOLD = TextColor.color(0xFFD166);
    private static final TextColor RED = TextColor.color(0xFF6B6B);
    private static final TextColor BLUE = TextColor.color(0xA6DEFF);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z");

    private Messages() { }

    static Component access(ApiClient.AccessDecision decision, String username) {
        return switch (decision.status()) {
            case "SIGNUP_CODE" -> signupCode(decision.code());
            case "BANNED" -> restriction("This ban is permanent.");
            case "TIMEOUT" -> restriction("Your timeout continues until " + formatDate(decision.expiresAtUnixMs()) + ".");
            case "MAINTENANCE" -> maintenance();
            default -> signupRequired(username, decision.websiteUrl());
        };
    }

    static Component maintenance() {
        return Component.text("The MMU Minecraft Society server is under maintenance.\n\n", RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("Please try again later."));
    }

    static Component unavailable() {
        return Component.text("The Minecraft server you are joining is temporarily unavailable.\n\n", RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("Please try again later. If this continues, tell the committee on "))
                .append(Component.text("Discord", BLUE)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://discord.gg/pPgZqRS5b2")))
                .append(Component.text("."));
    }

    static Component authenticationUnavailable() {
        return Component.text("Login authentication is temporarily unavailable.\n\n", RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("Please try again in a moment."));
    }

    static Component restriction(String status, Long expiresAtUnixMs) {
        return "BANNED".equals(status)
                ? restriction("This ban is permanent.")
                : restriction("Your timeout continues until " + formatDate(expiresAtUnixMs) + ".");
    }

    private static Component signupRequired(String username, String websiteUrl) {
        String url = websiteUrl == null || websiteUrl.isBlank()
                ? "https://mmuminecraftsociety.co.uk"
                : websiteUrl;
        return Component.text("(Don't worry, that is normal.)\n\n")
                .append(Component.text("Hi " + username + ", welcome to the MMU Minecraft Society!\n", GOLD)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("This network checks every player to keep hackers, demons and the "))
                .append(Component.text("chupacabra").decorate(TextDecoration.OBFUSCATED))
                .append(Component.text(" at bay.\n\n"))
                .append(Component.text("Your Minecraft account is not linked yet.\n\n", RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("Finish signup here:\n"))
                .append(Component.text(url, BLUE)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)))
                .append(Component.text("\n\nOnce verified, try joining again."));
    }

    private static Component signupCode(String code) {
        String displayCode = code == null ? "Code unavailable" : code.replace("|", " → ");
        return Component.text("Good job - almost finished.\n\n")
                .append(Component.text("Your signup code is:\n"))
                .append(Component.text(displayCode, GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("\n\nChoose these three items on the website. :D"));
    }

    private static Component restriction(String duration) {
        return Component.text("You cannot join this server.\n\n", RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(duration + "\n"))
                .append(Component.text("Contact the committee if you think this is an error."));
    }

    private static String formatDate(Long unixMs) {
        if (unixMs == null) return "an unknown time";
        return DATE.format(Instant.ofEpochMilli(unixMs).atZone(ZoneId.systemDefault()));
    }
}
