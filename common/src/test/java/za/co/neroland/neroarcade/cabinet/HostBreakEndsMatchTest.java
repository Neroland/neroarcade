package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §3.5: breaking the host, taking its cartridge out or breaking the hub mid-match ends it ABANDONED — no
 * exception, every seat unbound, no board write; the simulation never migrates.
 */
class HostBreakEndsMatchTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void hostBreakAbandonsWithoutAWrite() {
        BusMatchFixtures bus = new BusMatchFixtures("coop");
        assertTrue(bus.host.busSeatsInMatch(), "a node plays in this match (a hub break must end it)");
        // the host cabinet's teardown: end, then every node notices its host is gone and drops its seat
        assertDoesNotThrow(() -> {
            bus.host.end(MatchResult.Outcome.ABANDONED);
            bus.host.unbind(0, DropReason.BROKEN);
            bus.host.unbind(1, DropReason.HOST_GONE);
            HostFixtures.tick(bus.host, bus.env, MatchHost.RESULTS_TICKS + 5);
        });
        assertEquals(0, bus.writer.calls, "an abandoned match writes no board row");
        assertFalse(bus.host.hasSeat(), "every seat unbound");
        assertEquals(BusPhase.IDLE, bus.host.phase());
        MatchPhasePayload last = bus.env.sentTo(HostFixtures.BOB, MatchPhasePayload.class)
                .stream().reduce((a, b) -> b).orElseThrow();
        assertEquals(-1, last.mySeat(), "the node player was told they left");
    }

    @Test
    void theNodesSeeTheAbandonedResultsStrip() {
        BusMatchFixtures bus = new BusMatchFixtures("coop");
        bus.env.viewers.add(HostFixtures.CAROL); // a spectator at a node
        bus.host.end(MatchResult.Outcome.ABANDONED);
        MatchPhasePayload seen = bus.env.sentTo(HostFixtures.CAROL, MatchPhasePayload.class)
                .stream().reduce((a, b) -> b).orElseThrow();
        assertEquals(BusPhase.RESULTS, seen.phase());
        assertEquals(MatchResult.Outcome.ABANDONED.ordinal(), seen.outcome());
        assertEquals(MatchHost.RESULTS_TICKS, seen.ticksLeftInPhase());
    }

    @Test
    void lastHumanWalkingOutAbandons() {
        BusMatchFixtures bus = new BusMatchFixtures("coop");
        bus.host.unbind(0, DropReason.RANGE);
        assertEquals(BusPhase.PLAYING, bus.host.phase(), "one human left: coop keeps running");
        bus.host.unbind(1, DropReason.RANGE);
        assertEquals(BusPhase.RESULTS, bus.host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, bus.host.lastResult().orElseThrow().outcome());
        assertEquals(0, bus.writer.calls);
    }
}
