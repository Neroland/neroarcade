package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.arena.Ring24Arena;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/serpent}. */
class SerpentPawnTest {

    private static HeadlessMatch playing(ResolvedGame game, int seats) {
        return HeadlessMatch.of(game, seats, 1L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    /**
     * Rebuilds a seat's serpent: head at {@code cells[0]}, segments (order 1..) at the rest, heading {@code dir},
     * with {@code acc} preset so the next tick at speed 3 takes exactly one step ({@code 64 + 192 = 256}) or none.
     */
    private static void body(MatchState s, int seat, int dir, int acc, int[]... cells) {
        int head = s.pawnId(seat);
        int[] old = SerpentPawn.segments(s, seat);
        for (int id : old) {
            s.remove(id);
        }
        s.set(head, Entities.X, centre(cells[0][0]));
        s.set(head, Entities.Y, centre(cells[0][1]));
        s.setAux(head, GridStep.withAcc(GridStep.withDir(GridStep.withCounter(s.aux(head), 0), dir), acc));
        for (int k = 1; k < cells.length; k++) {
            int seg = s.spawn(EntityKind.SEGMENT, centre(cells[k][0]), centre(cells[k][1]), seat, 0);
            s.setAux(seg, GridStep.withData(s.aux(seg), k));
        }
    }

    private static void oneStep(HeadlessMatch match, int buttons) {
        MatchState s = match.state();
        int head = s.pawnId(0);
        s.setAux(head, GridStep.withAcc(s.aux(head), 64));
        match.step(SeatInput.of(buttons, 0, 0));
    }

    private static int headX(MatchState s, int seat) {
        return s.get(s.pawnId(seat), Entities.X) >> MatchState.FP_SHIFT;
    }

    @Test
    void spawnsTowardTheCentreWithItsBody() {
        MatchState s = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE), 1).state();
        assertEquals(GridStep.RIGHT, GridStep.dir(s.aux(s.pawnId(0))), "from (1,1) the tie goes right");
        assertEquals(2, SerpentPawn.segments(s, 0).length, "start_length 3");
    }

    @Test
    void growsOnCollect() {
        HeadlessMatch match = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE), 1);
        MatchState s = match.state();
        int before = SerpentPawn.segments(s, 0).length;
        int head = s.pawnId(0);
        int pickup = s.spawn(EntityKind.PICKUP, s.get(head, Entities.X), s.get(head, Entities.Y), -1, 0);
        match.step();
        assertFalse(s.exists(pickup), "collected");
        assertEquals(before, SerpentPawn.segments(s, 0).length, "growth waits for the next step");
        match.run(4, HeadlessMatch.InputScript.idle());
        assertEquals(before + 1, SerpentPawn.segments(s, 0).length);
        assertTrue(s.seatAlive(0));
    }

    @Test
    void selfBiteLosesLife() {
        HeadlessMatch match = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE), 1);
        MatchState s = match.state();
        body(s, 0, GridStep.RIGHT, 0, new int[] {5, 5}, new int[] {4, 5}, new int[] {3, 5}, new int[] {2, 5},
                new int[] {1, 5});
        oneStep(match, SeatInput.DOWN);
        oneStep(match, SeatInput.LEFT);
        assertTrue(s.seatAlive(0), "boxed in a 2x2 but not yet bitten");
        oneStep(match, SeatInput.UP);
        assertFalse(s.seatAlive(0), "the head turned into its own tail");
    }

    @Test
    void reversalRefused() {
        HeadlessMatch match = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE), 1);
        MatchState s = match.state();
        body(s, 0, GridStep.RIGHT, 0, new int[] {6, 6}, new int[] {5, 6}, new int[] {4, 6});
        oneStep(match, SeatInput.LEFT);
        assertEquals(GridStep.RIGHT, GridStep.dir(s.aux(s.pawnId(0))));
        assertEquals(7, headX(s, 0), "kept going right");
        assertTrue(s.seatAlive(0));
    }

    @Test
    void twoQuickTurnsCannotReverseIntoTheNeck() {
        HeadlessMatch match = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE), 1);
        MatchState s = match.state();
        body(s, 0, GridStep.RIGHT, 0, new int[] {6, 6}, new int[] {5, 6}, new int[] {4, 6});
        match.step(SeatInput.of(SeatInput.UP, 0, 0)); // acc 0 + 192: no step yet, heading up
        match.step(SeatInput.of(SeatInput.LEFT, 0, 0)); // left from (6, 6) is the neck at (5, 6): refused
        assertEquals(GridStep.UP, GridStep.dir(s.aux(s.pawnId(0))));
        assertEquals(6, headX(s, 0));
        assertEquals(5, s.get(s.pawnId(0), Entities.Y) >> MatchState.FP_SHIFT, "it stepped up, not back");
        assertTrue(s.seatAlive(0));
    }

    @Test
    void wrapsOnRing() {
        HeadlessMatch match = playing(TestModules.resolvedOf(Ring24Arena.INSTANCE, SerpentPawn.INSTANCE), 1);
        MatchState s = match.state();
        body(s, 0, GridStep.RIGHT, 0, new int[] {23, 5}, new int[] {22, 5}, new int[] {21, 5});
        oneStep(match, 0);
        assertEquals(0, headX(s, 0), "x 24 wraps to 0");
        assertTrue(s.seatAlive(0));
    }

    @Test
    void twoSeatHeadIntoBodyDirectional() {
        HeadlessMatch match = playing(TestModules.resolvedOf(SerpentPawn.INSTANCE, TestModules.multiplayer(false),
                TestModules.rule()), 2);
        MatchState s = match.state();
        // seat 1 lies still across row 8 (acc 0: 192 < 256, no step this tick)
        body(s, 1, GridStep.RIGHT, 0, new int[] {10, 8}, new int[] {9, 8}, new int[] {8, 8});
        // seat 0 steps down from (8, 7) into seat 1's tail segment
        body(s, 0, GridStep.DOWN, 64, new int[] {8, 7}, new int[] {8, 6}, new int[] {8, 5});
        match.step();
        assertFalse(s.seatAlive(0), "the head that ran into a body is hit");
        assertTrue(s.seatAlive(1), "the body is not");
    }

    @Test
    void replayStable() {
        long a = HeadlessMatch.of(TestModules.resolvedOf(SerpentPawn.INSTANCE, TestModules.rule()), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(TestModules.resolvedOf(SerpentPawn.INSTANCE, TestModules.rule()), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
