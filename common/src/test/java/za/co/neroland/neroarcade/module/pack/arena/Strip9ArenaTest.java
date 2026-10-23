package za.co.neroland.neroarcade.module.pack.arena;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

/** 008 §2.1 {@code arena/strip_9}. */
class Strip9ArenaTest {

    private static ResolvedGame game(RuleModule rule, PropertyValues ruleProps, String scroll, int period)
            throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(Strip9Arena.INSTANCE, rule);
        PropertyValues arena = g.arenaProps().with("scroll", new PropertyValue.EnumValue(scroll))
                .with("period", new PropertyValue.IntValue(period));
        return new ResolvedGame(g.def(), g.arena(), arena, g.pawn(), g.pawnProps(), g.rule(),
                ruleProps == null ? g.ruleProps() : ruleProps, g.hazards(), g.hazardProps(), g.pickups(),
                g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(), g.modifier(),
                g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    private static HeadlessMatch playing(ResolvedGame game, long seed) {
        return HeadlessMatch.of(game, 1, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static byte[] row(MatchState s, int y) {
        byte[] out = new byte[s.width()];
        for (int x = 0; x < out.length; x++) {
            out[x] = s.cell(x, y);
        }
        return out;
    }

    @Test
    void scrollDownShiftsRowsAndDamagesBuriedPawn() throws ValidationError {
        PropertyValues lives = PropertyValues.defaults(SurviveTimeRule.INSTANCE).with("lives", new PropertyValue.IntValue(3));
        HeadlessMatch match = playing(game(SurviveTimeRule.INSTANCE, lives, "down", 10), 3L);
        MatchState s = match.state();
        assertEquals(9, s.width());
        assertEquals(24, s.height());
        match.step(); // tick 0 scrolls
        byte[] fresh = row(s, 0);
        for (int i = 0; i < 9; i++) {
            match.step(); // ticks 1..9: no scroll
        }
        assertArrayEquals(fresh, row(s, 0), "no shift between periods");
        match.step(); // tick 10
        assertArrayEquals(fresh, row(s, 1), "row 1 is the old row 0");

        // a gate row about to scroll onto the pawn
        for (int x = 0; x < 9; x++) {
            s.setCell(x, 0, Cell.WALL);
        }
        int pawn = s.pawnId(0);
        s.set(pawn, Entities.X, (4 << MatchState.FP_SHIFT) + 128);
        s.set(pawn, Entities.Y, (1 << MatchState.FP_SHIFT) + 128);
        for (int i = 0; i < 9; i++) {
            match.step(); // ticks 11..19
        }
        assertEquals(3, s.lives(0));
        match.step(); // tick 20: the wall row lands on row 1
        assertEquals(2, s.lives(0), "buried by the gate");
    }

    @Test
    void newRowNeverBlocksMoreThanThree() throws ValidationError {
        HeadlessMatch match = playing(game(TestModules.rule(), null, "down", 4), 11L);
        MatchState s = match.state();
        int gates = 0;
        for (int step = 0; step < 1500; step++) {
            match.step();
            boolean previousHasWall = false;
            for (int y = 0; y < s.height(); y++) {
                int walls = 0;
                int first = -1;
                int last = -1;
                for (int x = 0; x < s.width(); x++) {
                    if (s.cell(x, y) == Cell.WALL) {
                        walls++;
                        first = first < 0 ? x : first;
                        last = x;
                    }
                }
                assertTrue(walls <= Strip9Arena.MAX_GATE, "row " + y + " holds " + walls + " walls");
                assertTrue(walls == 0 || last - first + 1 == walls, "a gate is one run of cells");
                assertFalse(previousHasWall && walls > 0, "two obstacle rows adjacent at " + y);
                previousHasWall = walls > 0;
                gates += y == 0 && walls > 0 ? 1 : 0;
            }
        }
        assertTrue(gates > 0, "gates do appear");
    }

    @Test
    void noScrollIsStatic() throws ValidationError {
        HeadlessMatch match = playing(game(TestModules.rule(), null, "none", 10), 2L);
        MatchState s = match.state();
        match.run(1200, HeadlessMatch.InputScript.idle());
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                assertEquals(Cell.LANE, s.cell(x, y));
            }
        }
        assertEquals(Cell.WALL, s.cell(-1, 3), "the board edge is the wall");
        assertEquals(Cell.WALL, s.cell(9, 3));
    }

    @Test
    void spawnsOnTheBottomLanes() throws ValidationError {
        MatchState s = playing(game(TestModules.rule(), null, "none", 10), 2L).state();
        int pawn = s.pawnId(0);
        assertEquals(2, s.get(pawn, Entities.X) >> MatchState.FP_SHIFT);
        assertEquals(22, s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT);
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game(TestModules.rule(), null, "up", 4), 1, 9L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game(TestModules.rule(), null, "up", 4), 1, 9L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
