package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * Stage 6 decision on review 003b P3: personal bests get a retention ({@code privacy.personalBestRetentionDays},
 * default and hard cap 365). The clock is when the best was <b>set</b>: a match that does not beat it does not
 * restart it, so no play history is kept. A best saved before Stage 6 has no time; the first sweep starts its clock
 * instead of deleting it, and the stamped time is saved.
 */
class PersonalBestRetentionTest {

    private static final long DAY = 86_400L;
    private static final long YEAR = 365 * DAY;

    @Test
    void aBestExpiresAYearAfterItWasSetAndBeatingItRestartsTheClock() {
        LeaderboardTable table = new LeaderboardTable();
        long t0 = 1_800_000_000L;
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, true),
                BoardFixtures.seat(BoardFixtures.BOB, 1, 40, true)), t0, 10, u -> null);
        // Alice plays again later without beating her best: no new clock (no play history)
        table.record(BoardFixtures.result(MatchResult.Outcome.LOSE,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 10, true)), t0 + 100 * DAY, 10, u -> null);
        assertEquals(Optional.of(t0), table.bestSetAt(BoardFixtures.CARTRIDGE, BoardFixtures.ALICE));
        // Bob beats his best later: his clock restarts
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 60, true)), t0 + 200 * DAY, 10, u -> null);

        assertEquals(0, table.purgeExpired(t0 + YEAR - 1, 90 * DAY, YEAR), "a day short of a year: kept");
        assertEquals(1, table.purgeExpired(t0 + YEAR + 1, 90 * DAY, YEAR));
        assertFalse(table.holds(BoardFixtures.ALICE), "Alice's year-old best is gone");
        assertTrue(table.holds(BoardFixtures.BOB), "Bob's newer best stays");
        assertEquals(1, table.purgeExpired(t0 + 200 * DAY + YEAR + 1, 90 * DAY, YEAR));
        assertEquals(0, table.boardCount(), "an empty board is dropped");
    }

    @Test
    void theTwoArgumentSweepKeepsBests() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 50, true)), 1L, 10, u -> null);
        assertEquals(0, table.purgeExpired(10 * YEAR, DAY));
        assertTrue(table.holds(BoardFixtures.ALICE));
    }

    @Test
    void aPreStageSixBestStartsItsClockAtTheFirstSweep() {
        CompoundTag root = new CompoundTag();
        ListTag boards = new ListTag();
        CompoundTag board = new CompoundTag();
        board.putString("id", BoardFixtures.CARTRIDGE);
        board.put("top", new ListTag());
        ListTag bests = new ListTag();
        CompoundTag best = new CompoundTag();
        best.putString("uuid", BoardFixtures.ALICE.toString());
        best.putInt("score", 12);
        bests.add(best); // no "at": saved before Stage 6
        board.put("bests", bests);
        boards.add(board);
        root.put("boards", boards);

        LeaderboardTable table = LeaderboardTable.load(root);
        assertEquals(Optional.of(LeaderboardTable.UNKNOWN_AT),
                table.bestSetAt(BoardFixtures.CARTRIDGE, BoardFixtures.ALICE));
        int version = table.version();
        long now = 1_900_000_000L;
        assertEquals(0, table.purgeExpired(now, 90 * DAY, YEAR), "never deleted for lack of a time");
        assertTrue(table.version() != version, "the stamp changes the version, so the store saves it");
        assertEquals(Optional.of(now), table.bestSetAt(BoardFixtures.CARTRIDGE, BoardFixtures.ALICE));
        LeaderboardTable reloaded = LeaderboardTable.load(table.save());
        assertEquals(Optional.of(now), reloaded.bestSetAt(BoardFixtures.CARTRIDGE, BoardFixtures.ALICE));
        assertEquals(1, reloaded.purgeExpired(now + YEAR + 1, 90 * DAY, YEAR));
    }
}
