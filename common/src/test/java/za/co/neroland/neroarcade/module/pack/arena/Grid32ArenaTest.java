package za.co.neroland.neroarcade.module.pack.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

/** 008 §2.1 {@code arena/grid_32}. */
class Grid32ArenaTest {

    private static ResolvedGame game(String obstacles) throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(Grid32Arena.INSTANCE);
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps().with("obstacles", new PropertyValue.EnumValue(obstacles)),
                g.pawn(), g.pawnProps(), g.rule(), g.ruleProps(), g.hazards(), g.hazardProps(), g.pickups(),
                g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(), g.modifier(),
                g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    private static void assertBorderIsWall(MatchState s) {
        for (int i = 0; i < 32; i++) {
            assertEquals(Cell.WALL, s.cell(i, 0));
            assertEquals(Cell.WALL, s.cell(i, 31));
            assertEquals(Cell.WALL, s.cell(0, i));
            assertEquals(Cell.WALL, s.cell(31, i));
        }
    }

    private static int interiorWalls(MatchState s) {
        int n = 0;
        for (int y = 1; y <= 30; y++) {
            for (int x = 1; x <= 30; x++) {
                n += s.cell(x, y) == Cell.WALL ? 1 : 0;
            }
        }
        return n;
    }

    @Test
    void pillarsAndMazeInsideBorder() throws ValidationError {
        MatchState pillars = HeadlessMatch.of(game("pillars"), 1, 1L).state();
        assertEquals(32, pillars.width());
        assertEquals(32, pillars.height());
        assertBorderIsWall(pillars);
        assertEquals(49, interiorWalls(pillars), "a pillar every 4 cells from (4, 4)");
        assertEquals(Cell.WALL, pillars.cell(4, 4));
        assertEquals(Cell.WALL, pillars.cell(28, 28));
        assertEquals(Cell.EMPTY, pillars.cell(5, 4));
        for (long seed = 1; seed <= 30; seed++) {
            MatchState maze = HeadlessMatch.of(game("maze_lite"), 1, seed).state();
            assertBorderIsWall(maze);
            int walls = interiorWalls(maze);
            assertTrue(walls > 0 && walls <= Grid32Arena.MAZE_BARS * Grid32Arena.MAZE_BAR_LENGTH,
                    "24 bars of 4 at most: " + walls);
            for (int y = 1; y <= 2; y++) {
                for (int x = 1; x <= 30; x++) {
                    assertEquals(Cell.EMPTY, maze.cell(x, y), "no bar starts above row 3");
                    assertEquals(Cell.EMPTY, maze.cell(y, x), "no bar starts left of column 3");
                }
            }
        }
        MatchState open = HeadlessMatch.of(game("none"), 1, 1L).state();
        assertEquals(0, interiorWalls(open));
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game("maze_lite"), 1, 7L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game("maze_lite"), 1, 7L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
