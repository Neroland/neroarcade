package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.runtime.MatchResult;

/** 003 §6.2: top rows older than the retention go; personal bests stay; empty boards are dropped. */
class LeaderboardExpiryPurgeTest {

    private static final long DAY = 86_400L;
    private static final long NINETY_DAYS = 90 * DAY;

    @Test
    void ninetyDaysIsTheDefaultAndAYearTheCap() {
        assertEquals(90, NeroArcadeConfig.LEADERBOARD_RETENTION_DAYS.get());
    }

    @Test
    void oldRowsGoPersonalBestsStay() {
        LeaderboardTable table = new LeaderboardTable();
        long now = 1_000 * DAY;
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 500, false)), now - NINETY_DAYS - 1, 10, u -> null);
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 100, false)), now - DAY, 10, u -> null);
        int before = table.version();
        assertEquals(1, table.purgeExpired(now, NINETY_DAYS));
        assertTrue(table.version() != before, "a purge changes the version (viewers get a fresh page)");
        LeaderboardTable.Board board = table.board(BoardFixtures.CARTRIDGE).orElseThrow();
        assertEquals(1, board.top().size());
        assertEquals(BoardFixtures.BOB, board.top().get(0).uuid());
        assertEquals(500, board.personalBest().get(BoardFixtures.ALICE), "personal bests are kept");
        assertEquals(0, table.purgeExpired(now, NINETY_DAYS), "nothing left to purge");
    }

    @Test
    void aRowExactlyAtTheCutoffStays() {
        LeaderboardTable table = new LeaderboardTable();
        long now = 500 * DAY;
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 5, false)), now - NINETY_DAYS, 10, u -> null);
        assertEquals(0, table.purgeExpired(now, NINETY_DAYS));
    }

    @Test
    void emptyBoardsAreDropped() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 5, false)), 0L, 10, u -> null);
        table.erase(BoardFixtures.ALICE);
        assertEquals(0, table.boardCount());
    }

    @Test
    void theStoreSurvivesASaveAndLoad() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 77, false)), 12L, 10, u -> "Alice");
        LeaderboardTable back = LeaderboardTable.load(table.save());
        LeaderboardTable.Board board = back.board(BoardFixtures.CARTRIDGE).orElseThrow();
        assertEquals(77, board.top().get(0).score());
        assertEquals(12L, board.top().get(0).at());
        assertEquals("Alice", board.top().get(0).name());
        assertEquals(77, board.personalBest().get(BoardFixtures.ALICE));
    }
}
