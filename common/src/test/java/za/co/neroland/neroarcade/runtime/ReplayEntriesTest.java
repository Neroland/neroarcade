package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

class ReplayEntriesTest {

    @Test
    void entryPastTheBudgetTruncatesAndReplayRefuses() {
        InputLog log = new InputLog();
        SeatInput previous = SeatInput.NONE;
        for (int t = 0; t < Budgets.MAX_REPLAY_ENTRIES; t++) {
            SeatInput now = (t % 2 == 0) ? SeatInput.of(SeatInput.UP, 0, 0) : SeatInput.NONE;
            log.record(t, 0, now, previous);
            previous = now;
        }
        assertFalse(log.truncated());
        assertEquals(Budgets.MAX_REPLAY_ENTRIES, log.entries().size());
        log.record(Budgets.MAX_REPLAY_ENTRIES, 0, previous.equals(SeatInput.NONE) ? SeatInput.of(SeatInput.UP, 0, 0)
                : SeatInput.NONE, previous);
        assertTrue(log.truncated());
        assertEquals(Budgets.MAX_REPLAY_ENTRIES, log.entries().size());
        Replay replay = new Replay(TestModules.emptyDef(), 1L, 1, log, 0L, 10);
        assertThrows(IllegalStateException.class, replay::play);
    }

    @Test
    void unchangedInputIsNotRecordedAndOrderIsEnforced() {
        InputLog log = new InputLog();
        log.record(0, 0, SeatInput.NONE, SeatInput.NONE);
        assertEquals(0, log.entries().size());
        log.record(5, 1, SeatInput.of(SeatInput.A, 0, 0), SeatInput.NONE);
        assertThrows(IllegalArgumentException.class, () -> log.record(5, 0, SeatInput.of(SeatInput.B, 0, 0), SeatInput.NONE));
        assertThrows(IllegalArgumentException.class, () -> log.record(4, 2, SeatInput.of(SeatInput.B, 0, 0), SeatInput.NONE));
        assertEquals(9, log.sizeBytes());
    }
}
