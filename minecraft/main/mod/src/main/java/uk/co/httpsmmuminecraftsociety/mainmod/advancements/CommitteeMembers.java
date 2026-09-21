package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import java.util.List;
import java.util.Locale;

public final class CommitteeMembers {
    public static final List<Member> ALL = List.of(
            new Member("MerlinSpace", "Freddy", "Chair"),
            new Member("oderzo", "oderzo", "Secretary"),
            new Member("HannahLucyyy", "Hannah", "Treasurer"),
            new Member("miaalicexoxo", "Mia", "Wellbeing Officer"),
            new Member("CalRay2", "Calum", "Social Media Manager")
    );

    private CommitteeMembers() {}

    public record Member(String username, String name, String role) {
        public String advancementPath() {
            return "social/committee/" + username.toLowerCase(Locale.ROOT);
        }
    }
}
