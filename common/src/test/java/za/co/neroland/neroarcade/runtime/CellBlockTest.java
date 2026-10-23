package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

/**
 * 008 §8 item 1: {@code Cell.BLOCK = 10} is a settled, solid, non-lethal stack cell. It blocks like WALL, survives
 * keyframes and deltas, and hashes differently from WALL.
 */
class CellBlockTest {

    @Test
    void blockIsAppendOnlyAndSolid() {
        assertEquals(10, Cell.BLOCK);
        assertTrue(Cell.blocks(Cell.BLOCK));
        assertEquals(-1, Cell.goalSeat(Cell.BLOCK), "not a goal line");
    }

    /** Where a cursor moving right from x=1 stops when {@code obstacle} sits at (4, 2). */
    private static int cursorStopsBefore(byte obstacle) {
        MatchState s = new MatchState(8, 8, 8, 1);
        s.setCell(4, 2, obstacle);
        int pawn = s.spawn(EntityKind.PAWN, RuntimeStubs.at(1), RuntimeStubs.at(2), 0, 0);
        s.setPawnId(0, pawn);
        ModuleContext ctx = new ModuleContext(s, null, Rng.view(s), null, 0, 0);
        s.set(pawn, Entities.VX, MatchState.FP);
        for (int i = 0; i < 6; i++) {
            CursorPawn.INSTANCE.tick(ctx, 0);
        }
        return s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
    }

    @Test
    void blocksMovementLikeWall() {
        assertEquals(3, cursorStopsBefore(Cell.BLOCK), "the cursor stops in front of the block");
        assertEquals(cursorStopsBefore(Cell.WALL), cursorStopsBefore(Cell.BLOCK), "exactly like WALL");
        assertEquals(7, cursorStopsBefore(Cell.EMPTY), "and only because of it (the border is out of range)");
    }

    @Test
    void aBlockedPawnLosesNoLife() {
        HeadlessMatch match = RuntimeStubs.playing(HeadlessMatch.of(
                za.co.neroland.neroarcade.module.TestModules.resolvedOf(), 1, 3L));
        MatchState s = match.state();
        int pawn = s.pawnId(0);
        int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
        int py = s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
        int lives = s.lives(0);
        s.setCell(px + 1, py, Cell.BLOCK);
        match.run(20, (tick, seat, rng) -> SeatInput.of(SeatInput.RIGHT, 0, 0));
        assertEquals(px, s.get(pawn, Entities.X) >> MatchState.FP_SHIFT, "held against the block");
        assertEquals(lives, s.lives(0), "non-lethal");
        assertTrue(s.exists(pawn));
    }

    private static MatchState boardWithBlocks() {
        MatchState s = new MatchState(16, 16, 32, 2);
        for (int x = 3; x < 9; x++) {
            s.setCell(x, 14, Cell.BLOCK);
        }
        s.setCell(5, 13, Cell.BLOCK);
        s.setCell(0, 0, Cell.WALL);
        return s;
    }

    @Test
    void survivesKeyframeRoundTrip() {
        MatchState s = boardWithBlocks();
        byte[] bytes = SnapshotCodec.writeKeyframe(s);
        MatchState decoded = MatchState.readKeyframe(ByteBuffer.wrap(bytes), Budgets.HARD_MAX_ENTITIES);
        assertEquals(Cell.BLOCK, decoded.cell(5, 13));
        assertEquals(Cell.BLOCK, decoded.cell(8, 14));
        assertEquals(Cell.WALL, decoded.cell(0, 0));
        assertEquals(s.stateHash(), decoded.stateHash());
    }

    @Test
    void survivesDeltaRoundTrip() {
        MatchState base = boardWithBlocks();
        MatchState current = base.copy();
        current.tick = base.tick + 1;
        current.setCell(5, 13, Cell.EMPTY); // a row clear drops / removes blocks
        current.setCell(9, 14, Cell.BLOCK);
        assertTrue(current.deltaCompatibleWith(base));
        MatchState applied = base.applyDelta(ByteBuffer.wrap(SnapshotCodec.writeDelta(base, current)));
        assertEquals(Cell.EMPTY, applied.cell(5, 13));
        assertEquals(Cell.BLOCK, applied.cell(9, 14));
        assertEquals(StateHash.ofView(current, false), StateHash.ofView(applied, false));
    }

    @Test
    void distinctInTheHash() {
        MatchState block = new MatchState(16, 16, 8, 1);
        MatchState wall = block.copy();
        MatchState empty = block.copy();
        block.setCell(4, 4, Cell.BLOCK);
        wall.setCell(4, 4, Cell.WALL);
        assertNotEquals(block.stateHash(), wall.stateHash(), "BLOCK hashes differently from WALL");
        assertNotEquals(block.stateHash(), empty.stateHash());
        assertFalse(block.stateHash() == wall.stateHash());
    }
}
