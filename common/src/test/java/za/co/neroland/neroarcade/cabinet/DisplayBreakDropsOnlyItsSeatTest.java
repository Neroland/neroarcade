package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/**
 * 003 §3.5 / §4.4: breaking a display node drops its seat only — the match runs on for the others, the dropped
 * runtime seat is never revived, and the result marks it dropped (personal best only, never a top row).
 */
class DisplayBreakDropsOnlyItsSeatTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void nodeBreakDropsItsSeatTheMatchGoesOn() {
        BusMatchFixtures bus = new BusMatchFixtures("coop");
        bus.host.unbind(1, DropReason.BROKEN);
        assertEquals(BusPhase.PLAYING, bus.host.phase());
        assertNotNull(bus.host.seat(0), "the host's seat is untouched");
        assertNull(bus.host.seat(1));
        assertTrue((bus.host.runtime().state().droppedSeatMask() & 0b10) != 0, "runtime seat 1 dropped");
        assertEquals(-1, bus.host.runtimeSeatOf(1));
        assertFalse(bus.host.bind(1, new Seat(1, HostFixtures.CAROL, 0, HostFixtures.NODE)),
                "no join during a running match");

        HostFixtures.tick(bus.host, bus.env, HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, bus.host.phase());
        assertEquals(1, bus.writer.calls);
        BusMatchResult.SeatResult dropped = bus.writer.last.seats().stream()
                .filter(s -> s.uuid().equals(HostFixtures.BOB)).findFirst().orElseThrow();
        assertTrue(dropped.dropped(), "a dropped seat is marked: personal best only");
        BusMatchResult.SeatResult stayed = bus.writer.last.seats().stream()
                .filter(s -> s.uuid().equals(HostFixtures.ALICE)).findFirst().orElseThrow();
        assertFalse(stayed.dropped());
    }
}
