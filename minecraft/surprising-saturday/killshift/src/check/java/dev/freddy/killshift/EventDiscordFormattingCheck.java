package dev.freddy.killshift;

public final class EventDiscordFormattingCheck {
    public static void main(String[] args) {
        String join = EventDiscordFormatting.format("join", "MerlinSpace", "Committee", "#0080ff",
                "joined Killshift. (1 online)");
        assert join.startsWith("```ansi\n➡️ \u001b[34mMerlinSpace\u001b[0m\u001b[36m [Committee]\u001b[0m ");
        assert join.endsWith("joined Killshift. (1 online)\n```");
        String death = EventDiscordFormatting.format("death", "Panda", "Member", "#00c800",
                "was slain by MerlinSpace");
        assert death.contains("☠️ \u001b[32mPanda\u001b[0m\u001b[32m [Member]\u001b[0m was slain by MerlinSpace");
        assert EventDiscordFormatting.format("server", "", "", "", "```").contains("``\\`");
        assert EventDiscordFormatting.format("server", "", "", "", "x".repeat(3000)).length() <= 2000;
        assert EventDiscordFormatting.chatUsername("Panda", "Member").equals("Panda [Member]");
        System.out.println("Killshift Discord formatting checks passed.");
    }
}
