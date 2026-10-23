package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.network.BoardPagePayload;
import za.co.neroland.neroarcade.privacy.CreditTable;
import za.co.neroland.neroarcade.privacy.Tombstones;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §6.2–6.3: a name is stored on a leaderboard row only when the player opted in to credit at write time, a
 * board page carries no name otherwise, and turning credit off strips the stored names. Credit is off by default.
 */
class NoNameWithoutOptInTest {

    private static final Function<UUID, String> PROFILES = uuid -> uuid.equals(BoardFixtures.ALICE) ? "Alice"
            : "Bob_the_very_long_name";

    private static LeaderboardTable played(CreditTable credit) {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 400, false),
                BoardFixtures.seat(BoardFixtures.BOB, 1, 300, false)), 1000L, 10,
                Boards.names(credit::isOn, PROFILES));
        return table;
    }

    @Test
    void creditIsOffByDefault() {
        CreditTable credit = new CreditTable();
        assertFalse(credit.isOn(BoardFixtures.ALICE));
        assertEquals(0, credit.size());
    }

    @Test
    void optInOffMeansNoNameAnywhere() {
        LeaderboardTable table = played(new CreditTable());
        LeaderboardTable.Board board = table.board(BoardFixtures.LINEAGE).orElseThrow();
        board.top().forEach(e -> assertNull(e.name()));
        BoardPagePayload page = Boards.page(BoardFixtures.LINEAGE, table.board(BoardFixtures.LINEAGE),
                BoardFixtures.ALICE, 10);
        page.rows().forEach(r -> assertEquals("", r.name()));
        assertEquals(400, page.myBest());
    }

    @Test
    void optInOnStoresTheProfileNameCutTo16() {
        CreditTable credit = new CreditTable();
        credit.set(BoardFixtures.ALICE, true);
        credit.set(BoardFixtures.BOB, true);
        LeaderboardTable table = played(credit);
        BoardPagePayload page = Boards.page(BoardFixtures.LINEAGE, table.board(BoardFixtures.LINEAGE), null, 10);
        assertEquals("Alice", page.rows().get(0).name());
        assertEquals("Bob_the_very_lon", page.rows().get(1).name());
        assertEquals(-1, page.myBest());
    }

    @Test
    void turningCreditOffStripsStoredNames() {
        CreditTable credit = new CreditTable();
        credit.set(BoardFixtures.ALICE, true);
        LeaderboardTable table = played(credit);
        assertEquals("Alice", table.board(BoardFixtures.LINEAGE).orElseThrow().top().get(0).name());
        assertTrue(credit.set(BoardFixtures.ALICE, false));
        assertFalse(credit.holds(BoardFixtures.ALICE), "off is never stored");
        assertTrue(table.stripNames(BoardFixtures.ALICE));
        assertNull(table.board(BoardFixtures.LINEAGE).orElseThrow().top().get(0).name());
    }

    @Test
    void turningCreditOnNeverNamesOldRows() {
        CreditTable credit = new CreditTable();
        LeaderboardTable table = played(credit);
        credit.set(BoardFixtures.ALICE, true);
        assertNull(table.board(BoardFixtures.LINEAGE).orElseThrow().top().get(0).name());
    }

    @Test
    void theTombstoneCanNeverOptIn() {
        CreditTable credit = new CreditTable();
        assertFalse(credit.set(Tombstones.ERASED_AUTHOR, true));
        assertFalse(credit.isOn(Tombstones.ERASED_AUTHOR));
    }
}
