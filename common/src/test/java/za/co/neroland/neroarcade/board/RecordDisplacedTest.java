package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * The {@code RECORD_BEATEN} input (007 §4): a match reports every player whose top-10 row it pushed down or off,
 * with the new rank (null = off the window); a player who only improved, or whose rank held, is not reported.
 */
class RecordDisplacedTest {

    private static final UUID KESSLER = UUID.fromString("0badc0de-1234-4567-89ab-cdef01234567");

    @Test
    void overtakenRowsAreReported() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, false),
                BoardFixtures.seat(BoardFixtures.BOB, 1, 40, false)), 1L, 10, u -> null);
        RecordOutcome outcome = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(KESSLER, 0, 90, false)), 2L, 10, u -> "Kessler_K");
        assertEquals(List.of(new RecordOutcome.Displaced(BoardFixtures.ALICE, 2),
                new RecordOutcome.Displaced(BoardFixtures.BOB, 3)), outcome.displaced());

        RecordOutcome improved = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 45, false)), 3L, 10, u -> null);
        assertTrue(improved.displaced().isEmpty(), "Bob improved without passing anyone");
    }

    @Test
    void fallingOffTheWindowIsNull() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 10, false)), 1L, 1, u -> null);
        RecordOutcome outcome = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(KESSLER, 0, 90, false)), 2L, 1, u -> null);
        assertEquals(1, outcome.displaced().size());
        assertNull(outcome.displaced().get(0).newRank());
    }

    @Test
    void ranksShareTies() {
        var ranks = LeaderboardTable.ranks(List.of(new LeaderboardTable.Entry(BoardFixtures.ALICE, 5, 1L, null),
                new LeaderboardTable.Entry(BoardFixtures.BOB, 5, 2L, null),
                new LeaderboardTable.Entry(KESSLER, 3, 3L, null)), 10);
        assertEquals(1, ranks.get(BoardFixtures.ALICE));
        assertEquals(1, ranks.get(BoardFixtures.BOB));
        assertEquals(3, ranks.get(KESSLER));
    }
}
