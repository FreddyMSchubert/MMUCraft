package dev.freddy.killshift;

final class EventDiscordFormatting {
    private static final String RESET = "\u001b[0m";

    private EventDiscordFormatting() { }

    static String chatUsername(String name, String role) {
        String result = name + roleLabel(role);
        return result.substring(0, Math.min(result.length(), 80));
    }

    static String format(String type, String name, String role, String colorHex, String content) {
        if (type.equals("chat")) return content.substring(0, Math.min(content.length(), 2000));
        String icon = switch (type) {
            case "join" -> "➡️";
            case "leave" -> "⬅️";
            case "death" -> "☠️";
            case "score" -> "✅";
            default -> "";
        };
        String label = roleLabel(role);
        String player = name.isEmpty() ? "" : ansi(ansiColor(colorHex)) + name + RESET
                + (label.isEmpty() ? "" : ansi(roleColor(role)) + label + RESET) + " ";
        String prefix = icon.isEmpty() ? player : icon + " " + player;
        String escaped = content.replace("```", "``\\`");
        int limit = Math.max(0, 2000 - "```ansi\n\n```".length() - prefix.length());
        return "```ansi\n" + prefix + escaped.substring(0, Math.min(escaped.length(), limit)) + "\n```";
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "Committee", "Member", "External" -> " [" + role + "]";
            default -> "";
        };
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "Committee" -> 36;
            case "Member" -> 32;
            case "External" -> 30;
            default -> 37;
        };
    }

    private static String ansi(int color) {
        return "\u001b[" + color + "m";
    }

    private static int ansiColor(String color) {
        if (!color.matches("(?i)#[0-9a-f]{6}")) return 37;
        int rgb = Integer.parseInt(color.substring(1), 16);
        int red = (rgb >> 16) & 255;
        int green = (rgb >> 8) & 255;
        int blue = rgb & 255;
        if (Math.max(red, Math.max(green, blue)) < 96) return 37;
        int[][] choices = {
                {31, 255, 0, 0}, {33, 255, 255, 0}, {32, 0, 200, 0},
                {34, 0, 100, 255}, {35, 160, 32, 240}, {30, 128, 128, 128},
                {37, 255, 255, 255}
        };
        int best = 37;
        int bestDistance = Integer.MAX_VALUE;
        for (int[] candidate : choices) {
            int distance = (red - candidate[1]) * (red - candidate[1])
                    + (green - candidate[2]) * (green - candidate[2])
                    + (blue - candidate[3]) * (blue - candidate[3]);
            if (distance < bestDistance) {
                best = candidate[0];
                bestDistance = distance;
            }
        }
        return best;
    }
}
