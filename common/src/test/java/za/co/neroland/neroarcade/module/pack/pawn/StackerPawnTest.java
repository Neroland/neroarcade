package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/stacker}. */
class StackerPawnTest {

    /** Stacker on grid_16 with survive_time at {@code lives} (the default 60 s clock). */
    private static ResolvedGame game(int lives) throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(StackerPawn.INSTANCE);
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(), g.pawnProps(), g.rule(),
                g.ruleProps().with("lives", new PropertyValue.IntValue(lives)), g.hazards(), g.hazardProps(), g.pickups(),
                g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(), g.modifier(),
                g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    private static HeadlessMatch playing(ResolvedGame game) {
        return HeadlessMatch.of(game, 1, 1L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    private static int pivotX(MatchState s) {
        return s.get(s.pawnId(0), Entities.X) >> MatchState.FP_SHIFT;
    }

    private static int pivotY(MatchState s) {
        return s.get(s.pawnId(0), Entities.Y) >> MatchState.FP_SHIFT;
    }

    /** Replaces seat 0's piece by the pivot at (px, py) plus segments at the given offsets. */
    private static void piece(MatchState s, int px, int py, int[]... offsets) {
        s.forEach(EntityKind.SEGMENT, id -> {
            if (s.get(id, Entities.OWNER) == 0) {
                s.remove(id);
            }
        });
        int pawn = s.pawnId(0);
        s.set(pawn, Entities.X, centre(px));
        s.set(pawn, Entities.Y, centre(py));
        for (int[] o : offsets) {
            int seg = s.spawn(EntityKind.SEGMENT, centre(px + o[0]), centre(py + o[1]), 0, 0);
            s.setAux(seg, GridStep.withData(s.aux(seg), StackerPawn.pack(o[0], o[1])));
        }
    }

    private static int blocks(MatchState s) {
        int n = 0;
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                n += s.cell(x, y) == Cell.BLOCK ? 1 : 0;
            }
        }
        return n;
    }

    @Test
    void spawnsAtTheTopCentre() throws ValidationError {
        MatchState s = playing(game(3)).state();
        assertEquals(8, pivotX(s));
        assertEquals(1, pivotY(s), "the first non-WALL row at x = w/2");
    }

    @Test
    void pieceFallsOneRowPerPeriod() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        int y = pivotY(s);
        for (int i = 0; i < 21; i++) {
            match.step();
        }
        assertEquals(y, pivotY(s), "fall 1: 21 ticks of 12 is still under 256");
        match.step();
        assertEquals(y + 1, pivotY(s), "the 22nd tick moves it one row");
    }

    @Test
    void landWritesBlock() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        int size = 1 + s.count(EntityKind.SEGMENT);
        int first = s.pawnId(0);
        match.step(SeatInput.of(SeatInput.B, 0, 0));
        assertEquals(size, blocks(s), "every piece cell became BLOCK");
        for (int x = 1; x < 15; x++) {
            assertNotEquals(Cell.WALL, s.cell(x, 14), "the stack is BLOCK, never WALL");
        }
        assertNotEquals(first, s.pawnId(0), "the next piece spawned in the same tick");
        assertEquals(1, pivotY(s));
        assertEquals(1, s.score(0), "a landing scores 1");
        assertTrue(s.seatAlive(0));
    }

    @Test
    void fullRowClearsAndScores() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        piece(s, 8, 1, new int[] {1, 0}); // an I2 over columns 8 and 9
        for (int x = 1; x <= 14; x++) {
            if (x != 8 && x != 9) {
                s.setCell(x, 14, Cell.BLOCK);
            }
        }
        match.step(SeatInput.of(SeatInput.B, 0, 0));
        for (int x = 1; x <= 14; x++) {
            assertEquals(Cell.EMPTY, s.cell(x, 14), "the full row cleared at x " + x);
        }
        assertEquals(Cell.WALL, s.cell(0, 14), "WALL columns stay");
        assertEquals(Cell.WALL, s.cell(3, 15), "the border floor never clears");
        assertEquals(0, blocks(s));
        assertEquals(1 + 10, s.score(0), "1 for landing + row_bonus * 1 * 1");
    }

    @Test
    void clearDropsTheStackAbove() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        piece(s, 8, 1, new int[] {1, 0});
        for (int x = 1; x <= 14; x++) {
            if (x != 8 && x != 9) {
                s.setCell(x, 14, Cell.BLOCK);
            }
        }
        s.setCell(3, 13, Cell.BLOCK);
        s.setCell(3, 12, Cell.BLOCK);
        match.step(SeatInput.of(SeatInput.B, 0, 0));
        assertEquals(Cell.BLOCK, s.cell(3, 14));
        assertEquals(Cell.BLOCK, s.cell(3, 13));
        assertEquals(Cell.EMPTY, s.cell(3, 12));
        assertEquals(2, blocks(s));
    }

    @Test
    void rotateKicksOffWall() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        piece(s, 1, 5, new int[] {0, -1}, new int[] {0, 1}); // a vertical I3 against the left wall
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertEquals(2, pivotX(s), "rotating in place would enter x 0; x-1 too; the x+1 kick fits");
        assertEquals(5, pivotY(s));
        int[] xs = {0, 0};
        int[] n = {0};
        s.forEach(EntityKind.SEGMENT, id -> {
            if (s.get(id, Entities.OWNER) == 0) {
                assertEquals(5, s.get(id, Entities.Y) >> MatchState.FP_SHIFT, "now horizontal");
                xs[n[0]++] = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
            }
        });
        assertEquals(4, xs[0] + xs[1], "segments at x 1 and 3");
        piece(s, 8, 5, new int[] {-1, 0}, new int[] {1, 0});
        for (int y = 3; y <= 7; y++) {
            for (int x = 6; x <= 10; x++) {
                if (y != 5) {
                    s.setCell(x, y, Cell.BLOCK);
                }
            }
        }
        match.step(SeatInput.NONE);
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertEquals(8, pivotX(s), "boxed in: the rotation is refused");
        assertEquals(5, pivotY(s));
    }

    @Test
    void topOutDamagesNine() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        s.removeSeatBody(0);
        for (int x = 1; x <= 14; x++) {
            s.setCell(x, 1, Cell.BLOCK);
            s.setCell(x, 2, Cell.BLOCK);
        }
        ResolvedGame g = match.runtime().game();
        StackerPawn.INSTANCE.spawn(new ModuleContext(s, g.pawnProps(), Rng.view(s), g, s.tick(), 0), 0, 0, 0);
        assertEquals(0, s.lives(0), "nine damage empties three lives");
        assertFalse(s.seatAlive(0));
    }

    @Test
    void shiftsOnPressThenRepeatsAfterAHold() throws ValidationError {
        HeadlessMatch match = playing(game(3));
        MatchState s = match.state();
        piece(s, 8, 3);
        match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
        assertEquals(7, pivotX(s), "the press shifts at once");
        for (int i = 0; i < 5; i++) {
            match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
        }
        assertEquals(7, pivotX(s), "no repeat inside the 6-tick hold");
        for (int i = 0; i < 3; i++) {
            match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
        }
        assertEquals(6, pivotX(s), "then one every 3 ticks");
    }

    @Test
    void opponentFillsLowestColumn() throws ValidationError {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(StackerPawn.INSTANCE, TestChips.Versus.INSTANCE), 1, 5L)
                .run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        assertEquals(1, match.runtime().opponentSeat());
        MatchState s = match.state();
        match.run(400, HeadlessMatch.InputScript.idle());
        assertTrue(s.seatAlive(1), "the opponent never topped out");
        assertTrue(s.score(1) >= 8, "at least 8 landings in 400 ticks, scored " + s.score(1));
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game(9), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game(9), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
