package za.co.neroland.neroarcade.module.pack.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

/** 008 §2.1 {@code arena/grid_16} (the default, as implemented). */
class Grid16ArenaTest {

    private static ResolvedGame game(String obstacles) throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(Grid16Arena.INSTANCE);
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps().with("obstacles", new PropertyValue.EnumValue(obstacles)),
                g.pawn(), g.pawnProps(), g.rule(), g.ruleProps(), g.hazards(), g.hazardProps(), g.pickups(),
                g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(), g.modifier(),
                g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    @Test
    void borderPillarsAndNoWrap() throws ValidationError {
        MatchState s = HeadlessMatch.of(game("pillars"), 1, 1L).state();
        assertFalse(s.wrapsX());
        assertFalse(s.wrapsY());
        int interior = 0;
        for (int i = 0; i < 16; i++) {
            assertEquals(Cell.WALL, s.cell(i, 0));
            assertEquals(Cell.WALL, s.cell(i, 15));
            assertEquals(Cell.WALL, s.cell(0, i));
            assertEquals(Cell.WALL, s.cell(15, i));
        }
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                interior += s.cell(x, y) == Cell.WALL ? 1 : 0;
            }
        }
        assertEquals(9, interior, "pillars at 4, 8, 12 on both axes");
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game("maze_lite"), 1, 5L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game("maze_lite"), 1, 5L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
