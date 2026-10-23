package za.co.neroland.neroarcade.module.pack.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/** 008 §2.1 {@code arena/ring_24}. */
class Ring24ArenaTest {

    private static ResolvedGame game(String obstacles) throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(Ring24Arena.INSTANCE);
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps().with("obstacles", new PropertyValue.EnumValue(obstacles)),
                g.pawn(), g.pawnProps(), g.rule(), g.ruleProps(), g.hazards(), g.hazardProps(), g.pickups(),
                g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(), g.modifier(),
                g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    @Test
    void wrapsOnlyX() throws ValidationError {
        MatchState s = HeadlessMatch.of(game("none"), 1, 1L).state();
        assertEquals(24, s.width());
        assertEquals(8, s.height());
        assertTrue(s.wrapsX());
        assertFalse(s.wrapsY());
        assertEquals(24 * 256 - 1, s.wrapX(-1));
        assertEquals(s.cell(23, 3), s.cell(-1, 3));
        assertEquals(Cell.WALL, s.cell(3, -1));
        for (int x = 0; x < 24; x++) {
            assertEquals(Cell.WALL, s.cell(x, 0));
            assertEquals(Cell.WALL, s.cell(x, 7));
        }
        MatchState pillars = HeadlessMatch.of(game("pillars"), 1, 1L).state();
        for (int x : new int[] {2, 8, 14, 20}) {
            assertEquals(Cell.WALL, pillars.cell(x, 3));
            assertEquals(Cell.WALL, pillars.cell(x, 4));
        }
        assertEquals(Cell.EMPTY, pillars.cell(3, 3));
    }

    @Test
    void fourSpawnsDistinct() throws ValidationError {
        for (String obstacles : new String[] {"none", "pillars"}) {
            ResolvedGame g = game(obstacles);
            MatchState s = HeadlessMatch.of(g, 1, 1L).state();
            ModuleContext ctx = new ModuleContext(s, g.arenaProps(), Rng.view(s), g, 0, 0);
            Set<Integer> seen = new HashSet<>();
            for (int seat = 0; seat < 4; seat++) {
                int[] c = Ring24Arena.INSTANCE.spawnCell(ctx, seat);
                assertEquals(Cell.EMPTY, s.cell(c[0], c[1]), "seat " + seat);
                assertTrue(seen.add(c[1] * 24 + c[0]), "seat " + seat + " shares a spawn");
            }
            int[] first = Ring24Arena.INSTANCE.spawnCell(ctx, 0);
            assertEquals(3, first[0]);
            assertEquals(5, first[1]);
        }
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game("pillars"), 1, 4L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game("pillars"), 1, 4L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
