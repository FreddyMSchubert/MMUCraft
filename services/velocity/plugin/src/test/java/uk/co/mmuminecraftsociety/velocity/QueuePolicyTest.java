package uk.co.mmuminecraftsociety.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueuePolicyTest {
    @Test
    void respectsMemberOrderAndOlderRegularTurns() {
        assertTrue(QueuePolicy.chooseMember(1, 2, 2));
        assertTrue(QueuePolicy.chooseMember(2, 1, 0));
        assertFalse(QueuePolicy.chooseMember(2, 1, 2));
        assertFalse(QueuePolicy.chooseMember(Long.MAX_VALUE, 1, 0));
        assertTrue(QueuePolicy.chooseMember(1, Long.MAX_VALUE, 2));
    }
}
