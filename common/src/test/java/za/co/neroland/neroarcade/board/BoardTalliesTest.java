package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §9: each board keeps aggregate {wins, records} tallies (no player in them) behind the {@code match_won} and
 * {@code record_set} channels; a win needs a non-dropped first place; a record is a new top score.
 */
class BoardTalliesTest {

    @Test
    void winsAndRecordsAreCounted() {
        LeaderboardTable table = new LeaderboardTable();
        RecordOutcome first = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 100, false)), 10L, 10, u -> null);
        assertEquals(1, first.wins());
        assertEquals(1, first.records(), "the first row on a board is a record");
        RecordOutcome lower = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 50, false)), 11L, 10, u -> null);
        assertEquals(2, lower.wins());
        assertFalse(lower.recordSet(), "50 is not above 100");
        RecordOutcome higher = table.record(BoardFixtures.result(MatchResult.Outcome.LOSE,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 150, false)), 12L, 10, u -> null);
        assertFalse(higher.won(), "a loss is not a win");
        assertEquals(2, higher.records());
        RecordOutcome walkOut = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 500, true)), 13L, 10, u -> null);
        assertFalse(walkOut.won(), "a dropped first place is no win");
        assertFalse(walkOut.recordSet(), "and earns no top row");
        assertArrayEquals(new int[] {2, 2}, table.tally(BoardFixtures.CARTRIDGE));

        LeaderboardTable loaded = LeaderboardTable.load(table.save());
        assertArrayEquals(new int[] {2, 2}, loaded.tally(BoardFixtures.CARTRIDGE));
        assertTrue(table.erase(BoardFixtures.ALICE));
        assertArrayEquals(new int[] {2, 2}, table.tally(BoardFixtures.CARTRIDGE), "tallies hold no player");
    }
}
