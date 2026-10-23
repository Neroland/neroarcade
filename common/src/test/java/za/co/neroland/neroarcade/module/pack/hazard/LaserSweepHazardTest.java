package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
import za.co.neroland.neroarcade.runtime.Rng;

class LaserSweepHazardTest {

    /** grid_16 has 16-cell rows: gap 4, gStart in 2..10. */
    private static final int GAP = 4;

    private static ResolvedGame rows() {
        return TestModules.resolvedOf(LaserSweepHazard.INSTANCE);
    }

    /** Two seats spawning at (1, 1) and (14, 14). */
    private static ResolvedGame rowsTwoSeats() {
        return TestModules.resolvedOf(LaserSweepHazard.INSTANCE, TestModules.multiplayer(false));
    }

    private static boolean hidden(MatchState s, int id) {
        return (s.get(id, Entities.FLAGS) & Entities.F_HIDDEN) != 0;
    }

    private static int gStart(MatchState s) {
        return LaserSweepHazard.gapStart(s.aux(Kit.ids(s, EntityKind.HAZARD, 0).get(0)));
    }

    @Test
    void beamIsOneEntityPerCell() {
        HeadlessMatch match = Kit.playing(rows(), 1, 1L);
        MatchState s = match.state();
        List<Integer> beam = Kit.ids(s, EntityKind.HAZARD, 0);
        assertEquals(16, beam.size());
        boolean[] seen = new boolean[16];
        for (int id : beam) {
            int index = HazardAux.counter(s.aux(id));
            assertTrue(!seen[index], "cell index " + index + " twice");
            seen[index] = true;
            assertEquals(LaserSweepHazard.HORIZONTAL, LaserSweepHazard.line(s.aux(id)));
            assertEquals(14, Kit.cellY(s, id), "tick 0 sits on the qualifying row farthest from the spawn row 1");
            assertTrue((s.get(id, Entities.FLAGS) & Entities.F_PERSISTENT) != 0);
        }
        ResolvedGame both = Kit.with(rows(), LaserSweepHazard.INSTANCE, "axis", "both");
        assertEquals(32, Kit.count(Kit.playing(both, 1, 1L).state(), EntityKind.HAZARD, 0));
        match.run(50, HeadlessMatch.InputScript.idle());
        assertEquals(16, Kit.count(s, EntityKind.HAZARD, 0), "moved, never respawned");
    }

    @Test
    void sweepsBackAndForthWithinQualifyingRange() {
        HeadlessMatch match = Kit.playing(rows(), 1, 1L);
        MatchState s = match.state();
        boolean low = false;
        boolean high = false;
        int previous = LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, 0);
        for (int t = 0; t < 400; t++) {
            int p = LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, t);
            assertTrue(p >= 1 && p <= 14, "row " + p + " at tick " + t);
            assertTrue(Math.abs(p - previous) <= 1, "never teleports");
            low |= p == 1;
            high |= p == 14;
            previous = p;
        }
        assertTrue(low && high, "sweeps the whole qualifying range");
        assertEquals(14, LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, 14 * 5),
                "pass 1 starts on the last row again");
        assertEquals(13, LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, 15 * 5));
    }

    @Test
    void stepFloorIsTwoTicks() {
        ResolvedGame game = Kit.with(Kit.with(TestModules.resolvedOf(TestChips.SizedArena.INSTANCE,
                LaserSweepHazard.INSTANCE), TestChips.SizedArena.INSTANCE, "side", 32), LaserSweepHazard.INSTANCE,
                "period", 20);
        MatchState s = Kit.playing(game, 1, 1L).state();
        for (int t = 0; t < 58; t++) {
            assertEquals(1 + t / LaserSweepHazard.MIN_STEP,
                    LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 20, t), "tick " + t);
        }
    }

    /** Beam cells over the wall border are hidden in place (no parking), as are the gap cells. */
    @Test
    void hiddenOverWalls() {
        MatchState s = Kit.playing(rows(), 1, 1L).state();
        int g = gStart(s);
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            int index = HazardAux.counter(s.aux(id));
            boolean border = index == 0 || index == 15;
            boolean gap = index >= g && index < g + GAP;
            assertEquals(border || gap, hidden(s, id), "cell index " + index);
            assertEquals(index, Kit.cellX(s, id), "hidden in place, never parked");
            assertEquals(14, Kit.cellY(s, id));
        }
    }

    /** Solo: the beam starts on row 14 and reaches row 12 at tick 15; column 1 is never in the gap (gStart >= 2). */
    @Test
    void touchCostsLife() {
        HeadlessMatch match = Kit.playing(rows(), 1, 1L);
        MatchState s = match.state();
        s.setLives(0, 3);
        Kit.putPawn(s, 0, 1, 12);
        match.run(15, HeadlessMatch.InputScript.idle());
        assertEquals(3, s.lives(0), "rows 14, 14, 13 swept (step = 80 / 14 = 5 ticks a row)");
        match.step();
        assertEquals(2, s.lives(0), "the beam reached row 12 at tick 15");
    }

    /** 008b Q3: spawns on rows 1 and 14, so the beam starts on row 7 (score 6; tie with 8 goes low). */
    @Test
    void startsFarthestFromSpawns() {
        MatchState s = Kit.playing(rowsTwoSeats(), 2, 1L).state();
        assertEquals(2, s.seatCount());
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            assertEquals(7, Kit.cellY(s, id), "tick 0");
            assertEquals(6, LaserSweepHazard.startPhase(s.aux(id)), "k0 = 7 - lo");
        }
        assertEquals(7, LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, 0, 6));
        assertNotEquals(1, LaserSweepHazard.position(s, LaserSweepHazard.HORIZONTAL, 80, 0, 6), "not lo");
    }

    @Test
    void phaseIsFrozenAtStart() {
        HeadlessMatch match = Kit.playing(rowsTwoSeats(), 2, 1L);
        MatchState s = match.state();
        match.run(50, HeadlessMatch.InputScript.idle());
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < s.width(); x++) {
                s.setCell(x, y, Cell.BLOCK);
            }
        }
        match.run(10, HeadlessMatch.InputScript.idle());
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            assertEquals(6, LaserSweepHazard.startPhase(s.aux(id)), "k0 never re-derived from the cells");
        }
    }

    /** 008b Q4: one gap of 4 hidden cells; a cursor standing in it while the beam sits on its row keeps its life. */
    @Test
    void gapIsHiddenAndHarmless() {
        HeadlessMatch match = Kit.playing(rowsTwoSeats(), 2, 1L);
        MatchState s = match.state();
        int g = gStart(s);
        assertTrue(g >= 2 && g <= 16 - 2 - GAP, "gStart " + g);
        int hiddenInside = 0;
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            int index = HazardAux.counter(s.aux(id));
            if (index >= 1 && index <= 14 && hidden(s, id)) {
                hiddenInside++;
                assertTrue(index >= g && index < g + GAP, "hidden index " + index + " outside the gap");
            }
        }
        assertEquals(GAP, hiddenInside, "exactly gap consecutive cells are hidden");
        s.setLives(0, 3);
        Kit.putPawn(s, 0, g, 7);
        match.run(5, HeadlessMatch.InputScript.idle());
        assertEquals(3, s.lives(0), "the beam sat on row 7 for five ticks and the gap let the cursor live");
        match.step();
        int row = Kit.cellY(s, Kit.ids(s, EntityKind.HAZARD, 0).get(0));
        assertEquals(8, row, "and has moved on");
    }

    /** Pass 1 begins at tick 40 (k = 40 / 5 + 6 = 14): exactly one draw for the beam, in range. */
    @Test
    void gapRedrawnEachPass() {
        HeadlessMatch match = Kit.playing(rowsTwoSeats(), 2, 1L);
        MatchState s = match.state();
        match.run(40, HeadlessMatch.InputScript.idle());
        MatchState before = s.copy();
        Rng expected = Rng.view(before);
        int draw = expected.nextInt(2, 16 - 2 - GAP);
        match.step();
        int g = gStart(s);
        assertTrue(g >= 2 && g <= 16 - 2 - GAP, "gStart " + g);
        assertEquals(draw, g, "the pass-start draw");
        assertEquals(expected.state(), Rng.view(s).state(), "one draw per beam per pass, nothing else");
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            assertEquals(g, LaserSweepHazard.gapStart(s.aux(id)), "every beam entity carries the new gap");
        }
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(Kit.with(rows(), LaserSweepHazard.INSTANCE, "axis", "both"), 1);
    }
}
