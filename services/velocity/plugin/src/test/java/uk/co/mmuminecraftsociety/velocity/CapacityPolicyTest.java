package uk.co.mmuminecraftsociety.velocity;

import java.util.List;
import java.util.UUID;

public final class CapacityPolicyTest {
    public static void main(String[] args) {
        var olderExternal = new CapacityPolicy.Candidate(UUID.randomUUID(), 0, 100, false);
        var newerExternal = new CapacityPolicy.Candidate(UUID.randomUUID(), 0, 200, false);
        var player = new CapacityPolicy.Candidate(UUID.randomUUID(), 1, 50, false);
        var member = new CapacityPolicy.Candidate(UUID.randomUUID(), 2, 40, false);
        var committee = new CapacityPolicy.Candidate(UUID.randomUUID(), 3, 30, false);
        var superAdmin = new CapacityPolicy.Candidate(UUID.randomUUID(), 4, 20, false);
        var exemptAdmin = new CapacityPolicy.Candidate(UUID.randomUUID(), 4, 10, true);
        var connected = List.of(member, newerExternal, superAdmin, committee,
                exemptAdmin, player, olderExternal);

        if (!CapacityPolicy.victims(connected, 6).equals(List.of(
                olderExternal.uuid(), newerExternal.uuid(), player.uuid(), member.uuid(),
                committee.uuid(), superAdmin.uuid()))) {
            throw new AssertionError("Eviction order must use priority, then longest session");
        }
        if (!CapacityPolicy.canAdmit(6, 6, 1, connected)) {
            throw new AssertionError("A player must be able to replace an external");
        }
        if (CapacityPolicy.canAdmit(6, 6, 0, connected)) {
            throw new AssertionError("An external cannot replace an equal-priority player");
        }
        if (CapacityPolicy.canAdmit(7, 6, 1, List.of(olderExternal, player, member))) {
            throw new AssertionError("One lower-priority player cannot cover two extra admissions");
        }
        System.out.println("Capacity policy checks passed");
    }
}
