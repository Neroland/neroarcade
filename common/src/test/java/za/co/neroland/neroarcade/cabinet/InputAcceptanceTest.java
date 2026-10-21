package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * 003 §2.4–2.5: a packet reaches the seat only from the seat's own player, for the current match, within the
 * per-second budget. Refusals are counted and never unseat (002 R6 {@code InputRateTest} lives here too).
 */
class InputAcceptanceTest {

    private static final SeatInput RIGHT = SeatInput.of(SeatInput.RIGHT, 0, 0);

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void wrongUuidIsDroppedAndNeverReachesTheSeat() {
        Seat seat = new Seat(0, HostFixtures.ALICE, 0);
        assertEquals(InputGate.Verdict.WRONG_PLAYER, InputGate.offer(seat, HostFixtures.BOB, 1, 1, RIGHT, 0, 30));
        assertSame(SeatInput.NONE, seat.latched(), "nothing latched for the victim");
        assertEquals(0, seat.droppedInputs(), "counted on the cabinet, not on the victim's seat");
    }

    @Test
    void spectatorHasNoSeat() {
        assertEquals(InputGate.Verdict.NO_SEAT, InputGate.offer(null, HostFixtures.BOB, 1, 1, RIGHT, 0, 30));
    }

    @Test
    void staleMatchIdIsDroppedAndCounted() {
        Seat seat = new Seat(0, HostFixtures.ALICE, 0);
        assertEquals(InputGate.Verdict.STALE_MATCH, InputGate.offer(seat, HostFixtures.ALICE, 1, 2, RIGHT, 0, 30));
        assertEquals(1, seat.droppedInputs());
        assertSame(SeatInput.NONE, seat.latched());
    }

    @Test
    void thirtyFirstPacketInASecondIsDroppedNextSecondAccepted() {
        Seat seat = new Seat(0, HostFixtures.ALICE, 0);
        for (int i = 0; i < 30; i++) {
            assertEquals(InputGate.Verdict.ACCEPTED, InputGate.offer(seat, HostFixtures.ALICE, 1, 1, RIGHT, 100 + i % 20, 30));
        }
        assertEquals(InputGate.Verdict.RATE_LIMITED, InputGate.offer(seat, HostFixtures.ALICE, 1, 1, RIGHT, 119, 30));
        assertEquals(1, seat.droppedInputs());
        assertEquals(InputGate.Verdict.ACCEPTED, InputGate.offer(seat, HostFixtures.ALICE, 1, 1, RIGHT, 120, 30),
                "a new one-second window");
    }

    @Test
    void hostOfferLatchesForTheOwnerAndFloodingNeverUnseats() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        Seat seat = new Seat(0, HostFixtures.ALICE, 0);
        host.bind(0, seat);
        int matchId = host.matchId();
        assertEquals(InputGate.Verdict.WRONG_PLAYER, host.offer(0, HostFixtures.BOB, matchId, RIGHT));
        assertEquals(InputGate.Verdict.ACCEPTED, host.offer(0, HostFixtures.ALICE, matchId, RIGHT));
        assertEquals(RIGHT, seat.latched());
        for (int i = 0; i < 200; i++) {
            host.offer(0, HostFixtures.ALICE, matchId, RIGHT);
        }
        assertSame(seat, host.seat(0), "excess input is dropped, never a kick");
        assertEquals(InputGate.Verdict.NO_SEAT, host.offer(1, HostFixtures.ALICE, matchId, RIGHT));
    }
}
