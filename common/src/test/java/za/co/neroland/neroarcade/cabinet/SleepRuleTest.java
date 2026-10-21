package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.ArcadeRuntime;

/** 002 §9 / R6: no seat and nobody near for {@code sleepAfterTicks} → asleep; anyone near resets the count. */
class SleepRuleTest {

    private static final int SLEEP_AFTER = 100;

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void fallsAsleepAfterExactlySleepAfterTicks() {
        SleepClock clock = new SleepClock();
        for (int t = 1; t < SLEEP_AFTER; t++) {
            assertFalse(clock.tick(false, false, SLEEP_AFTER), "tick " + t);
        }
        assertTrue(clock.tick(false, false, SLEEP_AFTER));
    }

    @Test
    void aSpectatorAtTick99ResetsTheCount() {
        SleepClock clock = new SleepClock();
        for (int t = 1; t < SLEEP_AFTER - 1; t++) {
            clock.tick(false, false, SLEEP_AFTER);
        }
        assertFalse(clock.tick(false, true, SLEEP_AFTER));
        assertEquals(0, clock.idleTicks());
        for (int t = 1; t < SLEEP_AFTER; t++) {
            assertFalse(clock.tick(false, false, SLEEP_AFTER));
        }
        assertTrue(clock.tick(false, false, SLEEP_AFTER));
    }

    @Test
    void aSeatKeepsItAwake() {
        SleepClock clock = new SleepClock();
        for (int t = 0; t < SLEEP_AFTER * 3; t++) {
            assertFalse(clock.tick(true, false, SLEEP_AFTER));
        }
    }

    /**
     * The cabinet loop in miniature: once asleep the host is dropped, so its runtime is never stepped again,
     * and the next use builds a fresh match (a sleeping match is never resumed).
     */
    @Test
    void sleepingCabinetStopsSteppingAndWakesFresh() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        ArcadeRuntime runtime = host.runtime();
        host.unbind(0, DropReason.RANGE);

        SleepClock clock = new SleepClock();
        AwakeCabinets slots = new AwakeCabinets(() -> 4);
        Object cabinet = new Object();
        slots.request(cabinet);
        int ticked = 0;
        while (host != null) {
            env.time++;
            host.tick();
            ticked++;
            if (clock.tick(host.hasSeat(), !env.viewers.isEmpty(), SLEEP_AFTER)) {
                host.end(za.co.neroland.neroarcade.runtime.MatchResult.Outcome.ABANDONED);
                host = null;
                slots.release(cabinet);
            }
        }
        assertEquals(SLEEP_AFTER, ticked);
        assertFalse(slots.isAwake(cabinet), "the awake slot is released");
        int steps = runtime.steps();
        env.time += 50;
        assertEquals(steps, runtime.steps(), "nothing steps a sleeping cabinet");

        MatchHost woken = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        woken.bind(0, new Seat(0, HostFixtures.BOB, env.time));
        assertEquals(BusPhase.COUNTDOWN, woken.phase());
        assertEquals(1, woken.matchId(), "a fresh match, not the old one resumed");
    }
}
