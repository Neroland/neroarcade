package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Closing the bench menu ends the test play on the server only for the player who holds the test seat. */
class SeatHeldByTest {

    @Test
    void menuCloseStopsOnlyTheHoldersTest() {
        Seat test = new Seat(0, HostFixtures.ALICE, 0);
        assertTrue(Seat.heldBy(test, HostFixtures.ALICE), "the tester closing the bench stops the test");
        assertFalse(Seat.heldBy(test, java.util.UUID.fromString("00000000-0000-4000-8000-000000000001")),
                "a bystander closing their view of the bench does not");
        assertFalse(Seat.heldBy(null, HostFixtures.ALICE), "no test running");
    }
}
