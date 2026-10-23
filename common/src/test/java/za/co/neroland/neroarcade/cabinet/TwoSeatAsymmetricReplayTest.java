package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * 003 §13: two cabinets on one bus, a different scripted input stream per seat, run twice from the same seed →
 * the same final hash, equal to {@link HeadlessMatch} fed the merged input rows. One simulation, two seats.
 */
class TwoSeatAsymmetricReplayTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static SeatInput script(int tick, int seat) {
        if (seat == 0) {
            return SeatInput.of(tick % 20 < 10 ? SeatInput.RIGHT : SeatInput.DOWN, 0, 0);
        }
        return SeatInput.of(tick % 14 < 7 ? SeatInput.LEFT : SeatInput.UP | SeatInput.A, 0, 0);
    }

    private record Run(long hostHash, long headlessHash, int seatsInResult) {
    }

    private static Run run() {
        ResolvedGame game = HostFixtures.tenSecondGame("coop", 2);
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = new MatchHost(env, HostFixtures.POS, game, writer, 2);
        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS)));
        assertTrue(host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE)));
        host.pressStart(0);
        assertEquals(BusPhase.LOBBY, host.phase(), "a two-seat game waits for both");
        host.pressStart(1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());

        HeadlessMatch reference = HeadlessMatch.of(game, 2, env.seed);
        int tick = 0;
        while (host.phase().running() && tick < 60 + HostFixtures.GAME_TICKS) {
            SeatInput a = script(tick, 0);
            SeatInput b = script(tick, 1);
            host.input(0, a);
            host.input(1, b);
            env.time++;
            host.tick();
            reference.step(a, b);
            tick++;
        }
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(1, writer.calls);
        return new Run(host.lastResult().orElseThrow().finalHash(), reference.hash(), writer.last.seats().size());
    }

    @Test
    void twoSeatsOneSimulationReplayIdentically() {
        Run first = run();
        Run second = run();
        assertEquals(first.hostHash(), second.hostHash(), "same seed, same inputs, same hash");
        assertEquals(first.hostHash(), first.headlessHash(), "the bus host equals the headless runtime");
        assertEquals(2, first.seatsInResult(), "both seats are in the result");
    }
}
