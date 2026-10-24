package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.privacy.BackupHook;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * Stage 6 kill switch {@code leaderboards.enabled}: off, a finished match writes no row, no personal best and no
 * backup; the boards already there are kept. On, the same match is recorded and backed up once.
 */
class LeaderboardKillSwitchTest {

    @AfterEach
    void restore() {
        LeaderboardStore.recordingForTest(null);
        BackupHook.set(null);
    }

    @Test
    void offWritesNothingOnWritesOnce() {
        List<String> backups = new ArrayList<>();
        BackupHook.set((server, store, content, backup) -> backups.add(store));
        LeaderboardStore store = new LeaderboardStore();

        LeaderboardStore.recordingForTest(() -> false);
        RecordOutcome off = store.record(null, BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, false)), u -> "Alice");
        assertEquals(RecordOutcome.NONE, off);
        assertFalse(store.holds(BoardFixtures.ALICE));
        assertTrue(backups.isEmpty());

        LeaderboardStore.recordingForTest(() -> true);
        store.record(null, BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, false)), u -> null);
        assertTrue(store.holds(BoardFixtures.ALICE));
        assertEquals(List.of("leaderboards"), backups);

        LeaderboardStore.recordingForTest(() -> false);
        store.record(null, BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 70, false)), u -> null);
        assertTrue(store.holds(BoardFixtures.ALICE), "existing boards are kept while the switch is off");
        assertFalse(store.holds(BoardFixtures.BOB));
    }
}
