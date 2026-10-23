package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class LaserSweepHazardTest {

    private static ResolvedGame rows() {
        return TestModules.resolvedOf(LaserSweepHazard.INSTANCE);
    }

    private static boolean hidden(MatchState s, int id) {
        return (s.get(id, Entities.FLAGS) & Entities.F_HIDDEN) != 0;
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
            assertEquals(LaserSweepHazard.HORIZONTAL, HazardAux.data(s.aux(id)));
            assertEquals(1, Kit.cellY(s, id), "tick 0 sits on the first qualifying row");
            assertTrue((s.get(id, Entities.FLAGS) & Entities.F_PERSISTENT) != 0);
        }
        ResolvedGame both = Kit.with(rows(), LaserSweepHazard.INSTANCE, "axis", "both");
        assertEquals(32, Kit.count(Kit.playing(both, 1, 1L).state(), EntityKind.HAZARD, 0));
        match.run(50, HeadlessMatch.InputScript.idle());
        assertEquals(16, Kit.count(s, EntityKind.HAZARD, 0), "moved, never respawned");
    }

    @Test
    void pingPongsWithinQualifyingRange() {
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

    @Test
    void hiddenOverWalls() {
        MatchState s = Kit.playing(rows(), 1, 1L).state();
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            int index = HazardAux.counter(s.aux(id));
            boolean border = index == 0 || index == 15;
            assertEquals(border, hidden(s, id), "cell index " + index);
            if (border) {
                assertEquals(1, Kit.cellX(s, id), "parked on the first open cell of the row");
                assertEquals(1, Kit.cellY(s, id));
            }
        }
    }

    @Test
    void touchCostsLife() {
        HeadlessMatch match = Kit.playing(rows(), 1, 1L);
        MatchState s = match.state();
        s.setLives(0, 3);
        Kit.putPawn(s, 0, 5, 5);
        match.run(20, HeadlessMatch.InputScript.idle());
        assertEquals(3, s.lives(0), "rows 1..4 swept past (step = 80 / 14 = 5 ticks a row)");
        match.step();
        assertEquals(2, s.lives(0), "the beam reached row 5 at tick 20");
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(Kit.with(rows(), LaserSweepHazard.INSTANCE, "axis", "both"), 1);
    }
}
