package uk.co.httpsmmuminecraftsociety.mainmod.money;

public final class AdvancementAnnouncementsCheck {
    public static void main(String[] args) {
        assert AdvancementAnnouncements.broadcast("minecraft", true, true, 0);
        assert !AdvancementAnnouncements.broadcast("minecraft", false, true, 100);
        assert !AdvancementAnnouncements.broadcast("minecraft", true, false, 100);
        assert !AdvancementAnnouncements.broadcast("blazeandcave", true, true, 29);
        assert AdvancementAnnouncements.broadcast("blazeandcave", false, false, 30);
        assert AdvancementAnnouncements.broadcast("mainmod", false, false, 100);
        System.out.println("Advancement announcement checks passed.");
    }
}
