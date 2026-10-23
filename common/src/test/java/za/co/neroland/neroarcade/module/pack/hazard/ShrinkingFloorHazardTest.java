package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

class ShrinkingFloorHazardTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(ShrinkingFloorHazard.INSTANCE);
    }

    /** Ring {@code r} of a 16×16 board. */
    private static void assertRing(MatchState s, int r, byte expected) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (ShrinkingFloorHazard.ring(x, y, 16, 16) == r) {
                    assertEquals(expected, s.cell(x, y), "(" + x + ", " + y + ")");
                }
            }
        }
    }

    @Test
    void firstRingAtInterval() {
        HeadlessMatch match = Kit.playing(game(), 1, 1L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 7, 7);
        match.run(100, HeadlessMatch.InputScript.idle());
        assertRing(s, 1, Cell.EMPTY);
        match.step();
        assertRing(s, 0, Cell.WALL);
        assertRing(s, 1, Cell.VOID);
        assertRing(s, 2, Cell.EMPTY);
        match.run(100, HeadlessMatch.InputScript.idle());
        assertRing(s, 2, Cell.VOID);
    }

    @Test
    void stopsAtMinSize() {
        ResolvedGame game = Kit.with(game(), ShrinkingFloorHazard.INSTANCE, "interval", 20);
        HeadlessMatch match = Kit.playing(game, 1, 1L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 7, 7);
        for (int t = 0; t < 400; t++) {
            ShrinkingFloorHazard.INSTANCE.tick(new ModuleContext(s,
                    game.hazardProps().get(0), Rng.view(s), game, t, 0));
        }
        for (int r = 1; r <= 5; r++) {
            assertRing(s, r, Cell.VOID);
        }
        assertRing(s, 6, Cell.EMPTY);
        assertRing(s, 7, Cell.EMPTY);
        assertEquals(Cell.EMPTY, s.cell(6, 6));
        assertEquals(Cell.EMPTY, s.cell(9, 9));
    }

    @Test
    void leavesExitsAndGoalsAlone() {
        ResolvedGame game = game();
        MatchState s = Kit.playing(game, 1, 1L).state();
        s.setCell(1, 5, Cell.EXIT);
        s.setCell(5, 1, Cell.GOAL_0);
        ShrinkingFloorHazard.INSTANCE.tick(new ModuleContext(s,
                game.hazardProps().get(0), Rng.view(s), game, 100, 0));
        assertEquals(Cell.EXIT, s.cell(1, 5));
        assertEquals(Cell.GOAL_0, s.cell(5, 1));
        assertEquals(Cell.VOID, s.cell(1, 1));
    }

    /** Needs the pawns' end-of-tick VOID check (001 A7), which WP-A adds to every pawn including the cursor. */
    @Test
    void pawnInVoidDies() {
        HeadlessMatch match = Kit.playing(game(), 1, 1L);
        MatchState s = match.state();
        s.setLives(0, 3);
        Kit.putPawn(s, 0, 1, 1);
        match.run(101, HeadlessMatch.InputScript.idle());
        assertEquals(Cell.VOID, s.cell(1, 1));
        match.step();
        assertTrue(s.lives(0) < 3, "a cursor standing on (1,1) loses a life once the ring falls");
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(Kit.with(game(), ShrinkingFloorHazard.INSTANCE, "interval", 20), 1);
    }
}
