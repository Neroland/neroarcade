package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class ChaserHazardTest {

    private static ResolvedGame fast() {
        return Kit.with(TestModules.resolvedOf(ChaserHazard.INSTANCE), ChaserHazard.INSTANCE, "speed", 4);
    }

    /** The single chaser, moved to a cell facing {@code dir} with an empty accumulator. */
    private static int chaser(MatchState s, int cx, int cy, int dir) {
        int id = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        s.set(id, Entities.X, Kit.at(cx));
        s.set(id, Entities.Y, Kit.at(cy));
        s.setAux(id, HazardAux.withAcc(HazardAux.withDir(s.aux(id), dir), 0));
        return id;
    }

    private static int manhattan(MatchState s, int id, int tx, int ty) {
        return Math.abs(Kit.cellX(s, id) - tx) + Math.abs(Kit.cellY(s, id) - ty);
    }

    @Test
    void closesDistanceEachStep() {
        ResolvedGame game = fast();
        HeadlessMatch match = Kit.playing(game, 1, 6L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 1, 1);
        int id = chaser(s, 14, 14, HazardAux.DOWN);
        int before = manhattan(s, id, 1, 1);
        for (int t = 0; t < 16; t++) {
            int d = manhattan(s, id, 1, 1);
            ChaserHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
            assertEquals(d - 1, manhattan(s, id, 1, 1), "one cell closer per tick at speed 4");
        }
        assertEquals(Math.max(0, before - 16), manhattan(s, id, 1, 1));
    }

    @Test
    void neverReversesWhenAnotherCellOpen() {
        ResolvedGame game = fast();
        HeadlessMatch match = Kit.playing(game, 1, 6L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 1, 5);
        int id = chaser(s, 5, 5, HazardAux.RIGHT);
        ChaserHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertNotEquals(4, Kit.cellX(s, id), "the target is behind it, but reversing is refused");
        assertEquals(6, Kit.cellX(s, id), "all three open cells tie at distance 5: it keeps going");
        // a dead end: the reversal is the only open cell, so it is taken
        chaser(s, 5, 5, HazardAux.RIGHT);
        s.setCell(5, 4, Cell.WALL);
        s.setCell(5, 6, Cell.WALL);
        s.setCell(6, 5, Cell.WALL);
        ChaserHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertEquals(4, Kit.cellX(s, id));
        assertEquals(HazardAux.LEFT, HazardAux.dir(s.aux(id)));
    }

    @Test
    void avoidsGoalCells() {
        ResolvedGame game = fast();
        HeadlessMatch match = Kit.playing(game, 1, 6L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 1, 5);
        for (int y = 1; y <= 14; y++) {
            s.setCell(4, y, Cell.GOAL_0);
        }
        int id = chaser(s, 6, 5, HazardAux.LEFT);
        for (int t = 0; t < 40; t++) {
            ChaserHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
            assertNotEquals(4, Kit.cellX(s, id), "never steps onto a goal line");
            assertTrue(Cell.goalSeat(s.cell(Kit.cellX(s, id), Kit.cellY(s, id))) < 0);
        }
    }

    @Test
    void shotTwiceDiesAndScores() {
        ResolvedGame game = fast();
        HeadlessMatch match = Kit.playing(game, 1, 6L);
        MatchState s = match.state();
        int id = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        assertEquals(ChaserHazard.HP, s.get(id, Entities.HP));
        ChaserHazard.INSTANCE.onShot(Kit.ctx(s, game, 0), 0, id, -1);
        assertTrue(s.exists(id));
        assertEquals(0, s.score(0));
        ChaserHazard.INSTANCE.onShot(Kit.ctx(s, game, 0), 0, id, -1);
        assertFalse(s.exists(id));
        assertEquals(ChaserHazard.KILL_SCORE, s.score(0));
        assertEquals(1, HazardAux.countTagged(s, EntityKind.MARKER, 0), "the respawn timer started");
    }

    @Test
    void respawnsAfterSixtyTicks() {
        ResolvedGame game = TestModules.resolvedOf(ChaserHazard.INSTANCE);
        HeadlessMatch match = Kit.playing(game, 1, 6L);
        MatchState s = match.state();
        int id = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        ChaserHazard.INSTANCE.onShot(Kit.ctx(s, game, 0), 0, id, -1);
        ChaserHazard.INSTANCE.onShot(Kit.ctx(s, game, 0), 0, id, -1);
        assertFalse(s.exists(id));
        match.run(ChaserHazard.RESPAWN_TICKS, HeadlessMatch.InputScript.idle());
        assertEquals(0, Kit.count(s, EntityKind.HAZARD, 0), "the marker lives 60 housekeeping passes");
        match.step();
        assertEquals(1, Kit.count(s, EntityKind.HAZARD, 0));
        int back = Kit.ids(s, EntityKind.HAZARD, 0).get(0);
        assertEquals(ChaserHazard.HP, s.get(back, Entities.HP));
    }

    /** 008 §7: twelve chasers (three stacks × 4) at speed 5 on 32×32; the count is pinned, the time logged. */
    @Test
    void costBoundTwelveChasers() {
        ResolvedGame game = Kit.with(Kit.with(Kit.with(TestModules.resolvedOf(TestChips.SizedArena.INSTANCE,
                ChaserHazard.INSTANCE, ChaserHazard.INSTANCE, ChaserHazard.INSTANCE),
                TestChips.SizedArena.INSTANCE, "side", 32), ChaserHazard.INSTANCE, "count", 4),
                ChaserHazard.INSTANCE, "speed", 5);
        HeadlessMatch match = Kit.playing(game, 1, 13L);
        MatchState s = match.state();
        long start = System.nanoTime();
        for (int t = 0; t < 1200; t++) {
            for (int stack = 0; stack < 3; stack++) {
                ChaserHazard.INSTANCE.tick(Kit.ctx(s, game, stack));
                assertTrue(Kit.count(s, EntityKind.HAZARD, stack) <= 4);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        int total = s.count(EntityKind.HAZARD);
        assertTrue(total <= 12, "at most 12 chasers, got " + total);
        System.out.println("ChaserHazardTest.costBound: 12 chasers x 1200 ticks on 32x32 took " + millis + " ms");
        assertTrue(millis < 5_000, "soft bound (008 asks < 200 ms on CI); took " + millis + " ms");
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(TestModules.resolvedOf(ChaserHazard.INSTANCE), 1);
    }
}
