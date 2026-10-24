package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * Owner decision D-owner-1 (2026-10-23): leaderboards are keyed by cartridge <b>id</b>. A duplicate or a download
 * keeps the id and posts to the same board; a remix mints a new id (keeping the original's lineage root) and posts
 * to a fresh board, so a remix with trivialised rules can never post onto the original's board. The world tallies
 * behind {@code match_won} / {@code record_set} follow the same key.
 */
class RemixGetsFreshBoardTest {

    private static final String ORIGINAL = "origorigorigorig";
    private static final String REMIX = "remixremixremixr";

    private static BusMatchResult on(String cartridgeId, int score) {
        // both cartridges share the original's lineage root
        return new BusMatchResult(ORIGINAL, cartridgeId, List.of(BoardFixtures.seat(BoardFixtures.ALICE, 0, score,
                false)), 400, 7L, MatchResult.Outcome.WIN, BusMatchResult.EndedBy.RULE, 1L);
    }

    @Test
    void aRemixPostsToAFreshBoardADuplicatePostsToTheSameOne() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(on(ORIGINAL, 100), 1L, 10, uuid -> null);
        // a duplicate or a download carries the same id: same board
        table.record(on(ORIGINAL, 150), 2L, 10, uuid -> null);
        assertEquals(150, table.board(ORIGINAL).orElseThrow().top().get(0).score());
        assertEquals(1, table.board(ORIGINAL).orElseThrow().top().size(), "one row per player per board");

        // the remix, with its trivial rules, scores 99 999 — on its own board
        table.record(on(REMIX, 99_999), 3L, 10, uuid -> null);
        assertEquals(150, table.board(ORIGINAL).orElseThrow().top().get(0).score(), "the original is untouched");
        assertEquals(99_999, table.board(REMIX).orElseThrow().top().get(0).score());
        assertEquals(REMIX, table.board(REMIX).orElseThrow().cartridgeId());
        assertEquals(2, table.boardCount());
        assertArrayEquals(new int[] {2, 2}, table.tally(ORIGINAL), "wins and records per cartridge id");
        assertArrayEquals(new int[] {1, 1}, table.tally(REMIX));
    }

    @Test
    void boardsSaveUnderTheIdAndReloadUnchanged() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(on(REMIX, 40), 1L, 10, uuid -> null);
        CompoundTag saved = table.save();
        CompoundTag board = saved.getList("boards").orElseThrow().getCompound(0).orElseThrow();
        assertEquals(REMIX, board.getString("id").orElseThrow());
        assertTrue(board.getString("root").isEmpty(), "the lineage root is never the key");
        LeaderboardTable back = LeaderboardTable.load(saved);
        assertEquals(40, back.board(REMIX).orElseThrow().top().get(0).score());
        assertArrayEquals(new int[] {1, 1}, back.tally(REMIX));
    }

    @Test
    void aPreDecisionSaveKeyedByLineageRootLoadsWithoutCrashing() {
        // the old shape: {"boards":[{"root":<lineage root>,"top":[...],"bests":[...]}],"tallies":[{"root":...}]}
        CompoundTag old = new CompoundTag();
        ListTag boards = new ListTag();
        CompoundTag board = new CompoundTag();
        board.putString("root", ORIGINAL);
        ListTag top = new ListTag();
        CompoundTag row = new CompoundTag();
        row.putString("uuid", BoardFixtures.ALICE.toString());
        row.putInt("score", 77);
        row.putLong("at", 5L);
        top.add(row);
        board.put("top", top);
        board.put("bests", new ListTag());
        boards.add(board);
        CompoundTag nameless = new CompoundTag(); // a malformed board: skipped, never a crash
        nameless.put("top", new ListTag());
        boards.add(nameless);
        old.put("boards", boards);
        ListTag tallies = new ListTag();
        CompoundTag tally = new CompoundTag();
        tally.putString("root", ORIGINAL);
        tally.putInt("wins", 3);
        tally.putInt("records", 1);
        tallies.add(tally);
        old.put("tallies", tallies);

        LeaderboardTable loaded = LeaderboardTable.load(old);
        // an original's lineage root is its own id, so its board carries over as that cartridge's board
        assertEquals(77, loaded.board(ORIGINAL).orElseThrow().top().get(0).score());
        assertArrayEquals(new int[] {3, 1}, loaded.tally(ORIGINAL));
        assertEquals(1, loaded.boardCount());
        // a remix of it starts fresh
        assertTrue(loaded.board(REMIX).isEmpty());
        // and the next save writes the new key
        CompoundTag resaved = loaded.save().getList("boards").orElseThrow().getCompound(0).orElseThrow();
        assertEquals(ORIGINAL, resaved.getString("id").orElseThrow());
    }
}
