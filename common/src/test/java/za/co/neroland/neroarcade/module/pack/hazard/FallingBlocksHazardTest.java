package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.hazard.HazardPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class FallingBlocksHazardTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(FallingBlocksHazard.INSTANCE);
    }

    /**
     * 008 names "rate 32 → one per tick", but {@code rate} is declared {@code [1..10]} (001 §7 wins), so this pins
     * the rate statistically at the maximum: 10/32 per tick over 320 ticks is ~100 draws, of which the 14 open
     * columns of grid_16 (the two wall columns spawn nothing) take ~87.
     */
    @Test
    void spawnsAtRate() {
        ResolvedGame game = Kit.with(game(), FallingBlocksHazard.INSTANCE, "rate", 10);
        HeadlessMatch match = Kit.playing(game, 1, 21L);
        MatchState s = match.state();
        Kit.putPawn(s, 0, 1, 14);
        int spawned = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int t = 0; t < 320; t++) {
            FallingBlocksHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
            for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
                if (seen.add(id)) {
                    spawned++;
                    assertTrue(Kit.cellY(s, id) <= 2, "spawned on the first open row (1), then fell half a cell");
                }
            }
        }
        assertTrue(spawned >= 60 && spawned <= 130, "spawned " + spawned);
    }

    @Test
    void capsAtFortyEight() {
        ResolvedGame game = Kit.with(game(), FallingBlocksHazard.INSTANCE, "rate", 10);
        HeadlessMatch match = Kit.playing(game, 1, 2L);
        MatchState s = match.state();
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            s.remove(id);
        }
        for (int i = 0; i < FallingBlocksHazard.CAP; i++) {
            int id = s.spawn(EntityKind.HAZARD, Kit.at(2 + i % 12), Kit.at(3 + i / 12), -1, 0);
            s.set(id, Entities.VY, 0);
        }
        for (int t = 0; t < 200; t++) {
            FallingBlocksHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
            assertEquals(FallingBlocksHazard.CAP, Kit.count(s, EntityKind.HAZARD, 0));
        }
    }

    @Test
    void fallSpeedFollowsGravity() {
        assertEquals(128, FallingBlocksHazard.fallSpeed(2, MatchState.DEFAULT_GRAVITY));
        assertEquals(64, FallingBlocksHazard.fallSpeed(2, 16));
        assertEquals(16, FallingBlocksHazard.fallSpeed(1, 0), "clamped at 16");
        assertEquals(256, FallingBlocksHazard.fallSpeed(5, 32), "clamped at a cell per tick");
        ResolvedGame game = Kit.with(game(), FallingBlocksHazard.INSTANCE, "rate", 10);
        HeadlessMatch match = Kit.playing(game, 1, 9L);
        MatchState s = match.state();
        s.setGravityPercent(50);
        for (int t = 0; t < 40; t++) {
            FallingBlocksHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        }
        assertFalse(Kit.ids(s, EntityKind.HAZARD, 0).isEmpty());
        for (int id : Kit.ids(s, EntityKind.HAZARD, 0)) {
            assertEquals(64, s.get(id, Entities.VY));
        }
    }

    @Test
    void removedAtFloor() {
        ResolvedGame game = Kit.with(game(), FallingBlocksHazard.INSTANCE, "rate", 1);
        HeadlessMatch match = Kit.playing(game, 1, 4L);
        MatchState s = match.state();
        int id = s.spawn(EntityKind.HAZARD, Kit.at(7), 14 << MatchState.FP_SHIFT, -1, 0);
        s.set(id, Entities.VY, 128);
        FallingBlocksHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertTrue(s.exists(id), "half a cell down, still above the wall row");
        assertEquals(14, Kit.cellY(s, id));
        FallingBlocksHazard.INSTANCE.tick(Kit.ctx(s, game, 0));
        assertFalse(s.exists(id), "the next step would enter the bottom wall row");
    }

    @Test
    void deflectCatchesAndScores() {
        ResolvedGame game = game();
        HeadlessMatch match = Kit.playing(game, 1, 4L);
        MatchState s = match.state();
        int id = s.spawn(EntityKind.HAZARD, Kit.at(7), Kit.at(7), -1, 0);
        FallingBlocksHazard.INSTANCE.onDeflected(Kit.ctx(s, game, 0), 0, id, s.pawnId(0));
        assertFalse(s.exists(id));
        assertEquals(1, s.score(0));
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(game(), 1);
    }
}
