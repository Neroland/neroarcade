package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * 003 §4.5: a {@code turns} game is hot-seat on one cabinet — one controller, every turn seat in the runtime, the
 * controller may change hands mid-match without ending it, and the result is never credited.
 */
class TurnsHotSeatTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void handoverKeepsTheMatchAndNothingIsCredited() {
        ResolvedGame game = HostFixtures.tenSecondGame("turns", 2);
        assertTrue(HotSeat.is(game));
        assertEquals(1, MatchHost.busSeatsFor(game, 4), "hot-seat binds one controller, whatever the bus");
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = new MatchHost(env, HostFixtures.POS, game, writer, MatchHost.busSeatsFor(game, 4));
        assertTrue(host.hotSeat());

        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS)));
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "one controller: the use is the start");
        HostFixtures.tick(host, env, 61);
        assertEquals(BusPhase.PLAYING, host.phase());
        assertEquals(2, host.runtime().state().seatCount(), "every turn seat is in the runtime");

        host.unbind(0, DropReason.ESC);
        assertEquals(BusPhase.PLAYING, host.phase(), "passing the controls does not end the match");
        assertEquals(0, host.runtime().state().droppedSeatMask(), "no runtime seat is dropped");
        assertTrue(host.bind(0, new Seat(0, HostFixtures.BOB, 0, HostFixtures.POS)), "the next player takes over");

        HostFixtures.tick(host, env, HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(0, writer.calls, "a hot-seat result writes no board row");
    }

    @Test
    void versusAndCoopAreNotHotSeat() {
        assertFalse(HotSeat.is(HostFixtures.tenSecondGame("versus", 2)));
        assertFalse(HotSeat.is(HostFixtures.tenSecondGame("coop", 2)));
        assertFalse(HotSeat.is(HostFixtures.tenSecondGame()));
    }
}
