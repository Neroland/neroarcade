package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.MatchPhasePayload;

/** 003 §4: the bus lobby — ready via START, minimum seats, the versus opponent wait, rematch consent. */
class MultiSeatLobbyTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static MatchPhasePayload lastTo(FakeHostEnv env, java.util.UUID who) {
        return env.sentTo(who, MatchPhasePayload.class).stream().reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void everySeatReadiesThenTheCountdownStarts() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("coop", 2),
                new HostFixtures.CountingWriter(), 2);
        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS)));
        assertEquals(BusPhase.LOBBY, host.phase(), "two seats: no start on use");
        host.pressStart(0);
        assertEquals(BusPhase.LOBBY, host.phase(), "below the minimum");
        assertTrue(host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE)));
        MatchPhasePayload lobby = lastTo(env, HostFixtures.BOB);
        assertEquals(0b11, lobby.boundMask());
        assertEquals(0b01, lobby.readyMask());
        assertEquals(1, lobby.mySeat());
        assertEquals(HostFixtures.NODE, lobby.seatAt(), "the node player's packets go to their own cabinet");
        host.pressStart(1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertFalse(host.bind(1, new Seat(1, HostFixtures.CAROL, 0, HostFixtures.NODE)), "no joining mid-match");
    }

    @Test
    void versusWaitsTenSecondsThenFieldsTheOpponent() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("versus", 2),
                new HostFixtures.CountingWriter(), 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.pressStart(0);
        HostFixtures.tick(host, env, MatchHost.OPPONENT_WAIT_TICKS - 1);
        assertEquals(BusPhase.LOBBY, host.phase(), "a free cabinet: wait for a second human");
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertEquals(1, host.runtime().opponentSeat(), "the runtime opponent fills seat 2");
    }

    @Test
    void packedRuntimeSeatsAndRematchNeedsFreshConsent() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        za.co.neroland.neroarcade.game.ResolvedGame game = HostFixtures.tenSecondGame("coop", 2);
        MatchHost host = new MatchHost(env, HostFixtures.POS, game, writer, MatchHost.busSeatsFor(game, 3));
        assertEquals(2, host.seatCount(), "min(chip seats, cabinets)");
        host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.pressStart(0);
        host.pressStart(1);
        assertEquals(0, host.runtimeSeatOf(0));
        assertEquals(1, host.runtimeSeatOf(1));
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(1, writer.calls);
        host.pressStart(1);
        assertEquals(BusPhase.RESULTS, host.phase(), "only the host seat (seat 0) can rematch");
        host.pressStart(0);
        assertEquals(BusPhase.LOBBY, host.phase());
        assertFalse(host.seat(0).ready() || host.seat(1).ready(), "every seat is un-ready: a fresh consent");
    }

    @Test
    void shrinkingTheBusUnbindsSeatsBeyondIt() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("coop", 2),
                new HostFixtures.CountingWriter(), 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.setBusSeats(1);
        assertEquals(1, host.seatCount());
        assertEquals(-1, lastTo(env, HostFixtures.BOB).mySeat());
        assertEquals(BusPhase.LOBBY, host.phase());
        host.pressStart(0);
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "one seat left on a one-seat bus: it starts when ready");
    }
}
