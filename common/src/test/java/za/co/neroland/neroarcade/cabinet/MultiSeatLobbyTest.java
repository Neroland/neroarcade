package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Final review M1: two friends on a four-cabinet hall with a 4-seat Versus chip play each other. */
    @Test
    void twoReadyHumansOnAFourSeatBusStartAfterTheWait() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        za.co.neroland.neroarcade.game.ResolvedGame game = HostFixtures.tenSecondGame("versus", 4);
        MatchHost host = new MatchHost(env, HostFixtures.POS, game, writer, MatchHost.busSeatsFor(game, 4));
        assertEquals(4, host.seatCount());
        assertEquals(4, host.minimumSeats());
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.pressStart(0);
        assertEquals(0, lastTo(env, HostFixtures.ALICE).ticksLeftInPhase(), "Bob is not ready: no wait yet");
        host.pressStart(1);
        assertEquals(BusPhase.LOBBY, host.phase());
        assertEquals(MatchHost.LOBBY_WAIT_TICKS, lastTo(env, HostFixtures.BOB).ticksLeftInPhase(),
                "the HUD gets the wait: starting in 10 s");
        HostFixtures.tick(host, env, MatchHost.LOBBY_WAIT_TICKS - 1);
        assertEquals(BusPhase.LOBBY, host.phase(), "two free cabinets: wait for more players");
        assertEquals(1, host.lobbyStartsInTicks());
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertEquals(2, host.runtime().state().seatCount(), "two runtime seats");
        assertEquals(-1, host.runtime().opponentSeat(), "two humans: no computer opponent");
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(1, writer.calls);
        assertEquals(2, writer.last.seats().size(), "two seat rows on the board");
    }

    @Test
    void unreadyingResetsTheLobbyWait() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("versus", 3),
                new HostFixtures.CountingWriter(), 3);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.bind(2, new Seat(2, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.pressStart(0);
        host.pressStart(2);
        HostFixtures.tick(host, env, 150);
        host.pressStart(2); // Bob un-readies
        assertEquals(0, host.lobbyStartsInTicks());
        HostFixtures.tick(host, env, 100);
        host.pressStart(2);
        HostFixtures.tick(host, env, MatchHost.LOBBY_WAIT_TICKS - 1);
        assertEquals(BusPhase.LOBBY, host.phase(), "the wait starts over");
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertEquals(0, host.runtimeSeatOf(0));
        assertEquals(1, host.runtimeSeatOf(2), "packed: cabinet 2 plays as P2");
    }

    /** Final review M1: a Co-op chip with more seats than humans plays solo after the wait. */
    @Test
    void oneReadyHumanOnACoopBusStartsSoloAfterTheWait() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("coop", 2), writer, 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        HostFixtures.tick(host, env, MatchHost.LOBBY_WAIT_TICKS + 5);
        assertEquals(BusPhase.LOBBY, host.phase(), "not ready: no wait");
        host.pressStart(0);
        HostFixtures.tick(host, env, MatchHost.LOBBY_WAIT_TICKS - 1);
        assertEquals(BusPhase.LOBBY, host.phase());
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertEquals(1, host.runtime().state().seatCount(), "solo");
        assertEquals(-1, host.runtime().opponentSeat(), "co-op fields no computer player");
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertTrue(host.lastResult().orElseThrow().outcome() != za.co.neroland.neroarcade.runtime.MatchResult.Outcome.ABANDONED);
        assertEquals(1, writer.calls);
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
        Seat bob = new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE);
        // the node cabinet Bob sits at: its seat field and the registry claim must go too (review 003b F7)
        SeatRegistry registry = new SeatRegistry();
        SeatRegistry.SeatKey nodeKey = new SeatRegistry.SeatKey("o", HostFixtures.NODE.asLong());
        registry.claim(HostFixtures.BOB, nodeKey);
        Seat[] nodeSeat = {bob};
        env.holders.put(HostFixtures.NODE, new SeatHolder() {
            @Override
            public MatchHost matchHost() {
                return host;
            }

            @Override
            public Seat heldSeat() {
                return nodeSeat[0];
            }

            @Override
            public void dropSeat(DropReason why) {
                Seat leaving = nodeSeat[0];
                if (leaving != null) {
                    nodeSeat[0] = null;
                    host.unbind(leaving.seat, why); // already unbound by the host: a no-op
                    registry.release(leaving.playerUuid, nodeKey);
                }
            }

            @Override
            public void countRejectedInput() {
            }
        });
        host.bind(1, bob);
        host.setBusSeats(1);
        assertEquals(1, host.seatCount());
        assertEquals(-1, lastTo(env, HostFixtures.BOB).mySeat());
        assertEquals(java.util.List.of(new FakeHostEnv.Unseat(HostFixtures.NODE, DropReason.HOST_GONE)), env.unseats);
        assertNull(nodeSeat[0], "the holder's seat is cleared");
        assertFalse(registry.isSeated(HostFixtures.BOB), "and the registry claim: Bob may sit elsewhere");
        assertNotNull(host.seat(0), "Alice is untouched");
        assertEquals(BusPhase.LOBBY, host.phase());
        host.pressStart(0);
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "one seat left on a one-seat bus: it starts when ready");
    }
}
