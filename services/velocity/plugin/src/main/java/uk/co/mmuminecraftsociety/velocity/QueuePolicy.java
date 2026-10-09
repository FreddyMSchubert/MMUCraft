package uk.co.mmuminecraftsociety.velocity;

final class QueuePolicy {
    private QueuePolicy() { }

    static boolean chooseMember(long memberOrder, long normalOrder, int turn) {
        return memberOrder != Long.MAX_VALUE
                && (normalOrder == Long.MAX_VALUE || normalOrder > memberOrder || turn != 2);
    }
}
