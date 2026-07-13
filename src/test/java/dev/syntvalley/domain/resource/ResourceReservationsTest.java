package dev.syntvalley.domain.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.TaskId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceReservationsTest {
    private static final TaskId TASK_A = new TaskId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final TaskId TASK_B = new TaskId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final ResourceKey OAK = new ResourceKey("minecraft:oak_log");

    @Test
    void twoTasksCannotOverReserveTheSameStock() {
        ResourceReservations reservations = new ResourceReservations();
        assertTrue(reservations.reserve(TASK_A, OAK, 3, 5, 100));
        assertEquals(3, reservations.reservedFor(OAK));

        assertFalse(reservations.reserve(TASK_B, OAK, 3, 5, 100), "3 + 3 exceeds 5 available");
        assertTrue(reservations.reserve(TASK_B, OAK, 2, 5, 100), "3 + 2 fits within 5");
        assertEquals(5, reservations.reservedFor(OAK));
    }

    @Test
    void ownerReplacesItsOwnReservationAndReleaseFrees() {
        ResourceReservations reservations = new ResourceReservations();
        reservations.reserve(TASK_A, OAK, 3, 5, 100);
        assertTrue(reservations.reserve(TASK_A, OAK, 5, 5, 100), "an owner may replace its own reservation");
        assertEquals(5, reservations.reservedFor(OAK));

        assertTrue(reservations.release(TASK_A));
        assertEquals(0, reservations.reservedFor(OAK));
    }

    @Test
    void expiryReleasesAtOrAfterExpiryTime() {
        ResourceReservations reservations = new ResourceReservations();
        reservations.reserve(TASK_A, OAK, 1, 5, 100);

        assertTrue(reservations.expire(99).isEmpty(), "not yet expired");
        assertTrue(reservations.expire(100).contains(TASK_A), "expired at the boundary");
        assertEquals(0, reservations.size());
    }
}
