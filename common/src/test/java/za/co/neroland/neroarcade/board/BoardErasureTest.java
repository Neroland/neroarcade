package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.privacy.CreditTable;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §6.4 (the straightforward part; Stage 6 runs Core's conformance harness): erasing a player removes their top
 * rows and personal bests from every board and their credit flag; both stores are registered for erasure under
 * stable names.
 */
class BoardErasureTest {

    @Test
    void erasureRemovesRowsBestsAndTheFlag() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, false),
                BoardFixtures.seat(BoardFixtures.BOB, 1, 40, false)), 1L, 10, u -> "x");
        table.record(new za.co.neroland.neroarcade.cabinet.BusMatchResult("ffff000011112222", "ffff000011112222",
                List.of(BoardFixtures.seat(BoardFixtures.ALICE, 0, 9, true)), 10, 1L, MatchResult.Outcome.LOSE,
                za.co.neroland.neroarcade.cabinet.BusMatchResult.EndedBy.RULE, 0L), 2L, 10, u -> null);
        CreditTable credit = new CreditTable();
        credit.set(BoardFixtures.ALICE, true);

        assertTrue(table.holds(BoardFixtures.ALICE));
        assertTrue(table.erase(BoardFixtures.ALICE));
        assertTrue(credit.erase(BoardFixtures.ALICE));

        assertFalse(table.holds(BoardFixtures.ALICE));
        assertFalse(credit.holds(BoardFixtures.ALICE));
        assertTrue(table.holds(BoardFixtures.BOB), "only the erased player goes");
        assertEquals(1, table.boardCount(), "the board left empty by the erasure is dropped");
        assertFalse(table.erase(BoardFixtures.ALICE), "erasing an unknown player is a no-op");
        assertFalse(LeaderboardTable.load(table.save()).holds(BoardFixtures.ALICE));
    }

    @Test
    void bothStoresAreRegisteredForErasure() {
        List<NeroArcadeData.StoreEraser> erasers = Boards.erasers();
        assertEquals(List.of("leaderboards", "credit"), erasers.stream().map(NeroArcadeData.StoreEraser::name).toList());
        // a server-less erasure (Core's conformance probe shape) must not throw
        erasers.forEach(e -> e.erase(null, BoardFixtures.ALICE));
    }
}
