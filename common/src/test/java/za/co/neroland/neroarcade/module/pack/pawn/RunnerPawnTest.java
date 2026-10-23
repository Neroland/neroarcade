package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/runner}. */
class RunnerPawnTest {

    private static HeadlessMatch playing() {
        return HeadlessMatch.of(TestModules.resolvedOf(RunnerPawn.INSTANCE), 1, 1L)
                .run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    private static void put(MatchState s, int cx, int cy, int dir) {
        int pawn = s.pawnId(0);
        s.set(pawn, Entities.X, centre(cx));
        s.set(pawn, Entities.Y, centre(cy));
        s.setAux(pawn, GridStep.withDir(s.aux(pawn), dir));
    }

    private static int cellX(MatchState s) {
        return s.get(s.pawnId(0), Entities.X) >> MatchState.FP_SHIFT;
    }

    private static int cellY(MatchState s) {
        return s.get(s.pawnId(0), Entities.Y) >> MatchState.FP_SHIFT;
    }

    @Test
    void reversesAtWall() {
        HeadlessMatch match = playing();
        MatchState s = match.state();
        assertEquals(GridStep.UP, GridStep.dir(s.aux(s.pawnId(0))), "starts running up");
        int y = cellY(s);
        assertEquals(1, y, "spawned under the top wall");
        match.step();
        assertEquals(GridStep.DOWN, GridStep.dir(s.aux(s.pawnId(0))), "the wall turned it round");
        match.run(6, HeadlessMatch.InputScript.idle());
        assertTrue(cellY(s) > y, "now running down");
        assertTrue(s.seatAlive(0), "a wall is not a hit");
    }

    private static boolean survivesCrateAhead(boolean hop) {
        HeadlessMatch match = playing();
        MatchState s = match.state();
        put(s, 8, 8, GridStep.DOWN);
        int crate = s.spawn(EntityKind.HAZARD, centre(8), centre(9), -1, 0);
        s.set(crate, Entities.FLAGS, Entities.F_LETHAL);
        match.step(hop ? SeatInput.of(SeatInput.A, 0, 0) : SeatInput.NONE);
        return s.seatAlive(0);
    }

    @Test
    void hopIgnoresHit() {
        assertTrue(survivesCrateAhead(true), "airborne: the crate passes under it");
        assertFalse(survivesCrateAhead(false), "grounded: one life lost");
    }

    @Test
    void hopDurationScalesWithGravity() {
        assertEquals(8, RunnerPawn.hopTicks(2, 32));
        assertEquals(16, RunnerPawn.hopTicks(2, 16));
        assertEquals(1, RunnerPawn.hopTicks(1, 255), "never below one tick");
        HeadlessMatch match = playing();
        MatchState s = match.state();
        put(s, 8, 8, GridStep.DOWN);
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertEquals(8, GridStep.counter(s.aux(s.pawnId(0))), "T = 8 at default gravity");
        int y = s.get(s.pawnId(0), Entities.Y);
        assertEquals(centre(8) + 3 * 64 + 2 * 256 / 8, y, "run speed plus jump * 256 / T");
        for (int i = 0; i < 7; i++) {
            match.step();
        }
        assertEquals(1, GridStep.counter(s.aux(s.pawnId(0))), "still airborne on its eighth tick");
        match.step();
        assertEquals(0, GridStep.counter(s.aux(s.pawnId(0))), "landed");
    }

    @Test
    void laneChangeOneColumnPerPress() {
        HeadlessMatch match = playing();
        MatchState s = match.state();
        put(s, 5, 12, GridStep.UP);
        for (int i = 0; i < 6; i++) {
            match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        }
        assertEquals(6, cellX(s), "holding shifts one column, once");
        match.step();
        match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        assertEquals(7, cellX(s), "a new press shifts another");
        match.step();
        match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
        match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        assertEquals(7, cellX(s), "the lockout swallows presses inside four ticks");
    }

    @Test
    void replayStable() {
        long a = HeadlessMatch.of(TestModules.resolvedOf(RunnerPawn.INSTANCE, TestModules.rule()), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(TestModules.resolvedOf(RunnerPawn.INSTANCE, TestModules.rule()), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
