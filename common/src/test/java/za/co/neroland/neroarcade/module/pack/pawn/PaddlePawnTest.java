package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/paddle}. */
class PaddlePawnTest {

    private static HeadlessMatch playing(HeadlessMatch match) {
        return match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    private static int cell(int units) {
        return units >> MatchState.FP_SHIFT;
    }

    @Test
    void paintsGoalRow() {
        MatchState s = HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE), 1, 1L).state();
        for (int x = 1; x <= 14; x++) {
            assertEquals(Cell.GOAL_0, s.cell(x, 14), "goal row h-2 at x " + x);
        }
        assertEquals(Cell.WALL, s.cell(0, 14), "the border is not painted");
        assertEquals(Cell.WALL, s.cell(15, 14));
        int pawn = s.pawnId(0);
        assertEquals(13, cell(s.get(pawn, Entities.Y)), "body row h-3");
        int segments = s.count(EntityKind.SEGMENT);
        assertEquals(2, segments, "length 3");
    }

    @Test
    void deflectsBall() {
        HeadlessMatch match = playing(HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE), 1, 1L));
        MatchState s = match.state();
        int pawn = s.pawnId(0);
        int px = cell(s.get(pawn, Entities.X));
        int ball = s.spawn(EntityKind.HAZARD, centre(px), centre(13), -1, 0);
        s.set(ball, Entities.VY, MatchState.FP);
        s.set(ball, Entities.FLAGS, Entities.F_LETHAL);
        match.step();
        assertTrue(s.seatAlive(0), "no life lost");
        assertEquals(1, s.lives(0));
        assertEquals(-MatchState.FP, s.get(ball, Entities.VY), "the ball's VY flipped");
        assertEquals(12, cell(s.get(ball, Entities.Y)), "and it moved back out");
    }

    @Test
    void bodyStopsAtWall() {
        HeadlessMatch match = playing(HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE), 1, 1L));
        MatchState s = match.state();
        for (int i = 0; i < 40; i++) {
            match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
            int[] min = {Integer.MAX_VALUE};
            min[0] = Math.min(min[0], cell(s.get(s.pawnId(0), Entities.X)));
            s.forEach(EntityKind.SEGMENT, id -> min[0] = Math.min(min[0], cell(s.get(id, Entities.X))));
            assertTrue(min[0] >= 1, "an end segment entered the wall at tick " + i);
        }
        assertEquals(2, cell(s.get(s.pawnId(0), Entities.X)), "centre two cells in, its end on x 1");
        for (int i = 0; i < 40; i++) {
            match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        }
        assertEquals(13, cell(s.get(s.pawnId(0), Entities.X)));
    }

    @Test
    void opponentTracksCrossing() {
        HeadlessMatch match = playing(HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE,
                TestChips.Versus.INSTANCE, TestModules.rule()), 1, 1L));
        assertEquals(1, match.runtime().opponentSeat());
        MatchState s = match.state();
        assertEquals(2, cell(s.get(s.pawnId(1), Entities.Y)), "seat 1 sits on row 2");
        assertEquals(Cell.GOAL_1, s.cell(5, 1));
        int ball = s.spawn(EntityKind.HAZARD, centre(4), centre(10), -1, 0);
        s.set(ball, Entities.VY, -64);
        s.set(ball, Entities.FLAGS, Entities.F_LETHAL);
        int start = cell(s.get(s.pawnId(1), Entities.X));
        assertTrue(Math.abs(start - 4) > 4, "starts far from the crossing");
        match.run(20, HeadlessMatch.InputScript.idle());
        int x = cell(s.get(s.pawnId(1), Entities.X));
        assertTrue(Math.abs(x - 4) <= 1, "under the crossing at x 4 within 20 ticks, at " + x);
    }

    @Test
    void replayStable() {
        long a = HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestModules.rule()), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(TestModules.resolvedOf(PaddlePawn.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestModules.rule()), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
