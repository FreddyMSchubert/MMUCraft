package dev.freddy.chunklocked;

public final class EventDiscordFormattingCheck {
    public static void main(String[] args) {
        String unlock = EventDiscordFormatting.format("unlock", "Panda", "Member", "#00c800",
                "unlocked Forest chunk #12!");
        assert unlock.contains("🧱 \u001b[32mPanda\u001b[0m\u001b[32m [Member]\u001b[0m unlocked Forest chunk #12!");
        String goal = EventDiscordFormatting.format("goal", "MerlinSpace", "Committee", "#0080ff",
                "completed \"Get Some Wood\"!");
        assert goal.contains("✅ \u001b[34mMerlinSpace\u001b[0m\u001b[36m [Committee]\u001b[0m completed \"Get Some Wood\"!");
        assert EventDiscordFormatting.chatUsername("Panda", "Member").equals("Panda [Member]");
        assert EventDiscordFormatting.format("server", "", "", "", "x".repeat(3000)).length() <= 2000;
        System.out.println("Chunklocked Discord formatting checks passed.");
    }
}
