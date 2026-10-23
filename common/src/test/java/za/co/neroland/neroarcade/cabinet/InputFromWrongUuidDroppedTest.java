package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * 003 §2.4 on a bus: input reaches a seat only from that seat's own player. Another player's packet, a stale match
 * id and a spectator's packet never latch and are counted.
 */
class InputFromWrongUuidDroppedTest {

    private FakeHostEnv env;
    private MatchHost host;
    private Seat bob;

    @BeforeEach
    void setUp() {
        env = new FakeHostEnv();
        host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("coop", 2),
                new HostFixtures.CountingWriter(), 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        bob = new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE);
        host.bind(1, bob);
        host.pressStart(0);
        host.pressStart(1);
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void anotherPlayersPacketForANodeSeatIsDropped() {
        SeatInput up = SeatInput.of(SeatInput.UP, 0, 0);
        assertEquals(InputGate.Verdict.WRONG_PLAYER, host.offer(1, HostFixtures.ALICE, host.matchId(), up));
        assertEquals(SeatInput.NONE, bob.latched(), "never latched");
        assertEquals(InputGate.Verdict.ACCEPTED, host.offer(1, HostFixtures.BOB, host.matchId(), up));
        assertEquals(up, bob.latched());
    }

    @Test
    void staleMatchIdIsDroppedAndCountedOnTheSeat() {
        assertEquals(InputGate.Verdict.STALE_MATCH,
                host.offer(1, HostFixtures.BOB, host.matchId() - 1, SeatInput.of(SeatInput.A, 0, 0)));
        assertEquals(1, bob.droppedInputs());
        assertEquals(SeatInput.NONE, bob.latched());
    }

    @Test
    void aSpectatorHasNoSeatToSendFor() {
        host.unbind(0, DropReason.ESC);
        assertEquals(InputGate.Verdict.NO_SEAT,
                host.offer(0, HostFixtures.CAROL, host.matchId(), SeatInput.of(SeatInput.UP, 0, 0)));
        assertEquals(InputGate.Verdict.NO_SEAT,
                host.offer(3, HostFixtures.CAROL, host.matchId(), SeatInput.of(SeatInput.UP, 0, 0)));
    }
}
