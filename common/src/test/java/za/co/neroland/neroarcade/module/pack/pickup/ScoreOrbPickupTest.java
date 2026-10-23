package za.co.neroland.neroarcade.module.pack.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.pickup.PickupPackTest.Kit;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class ScoreOrbPickupTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(ScoreOrbPickup.INSTANCE);
    }

    @Test
    void placesCountClearOfTheSpawn() {
        ResolvedGame game = Kit.with(game(), ScoreOrbPickup.INSTANCE, "count", 32);
        MatchState s = Kit.playing(game, 1, 4L).state();
        int placed = Kit.count(s, EntityKind.PICKUP, 0);
        assertTrue(placed > 0 && placed <= 32, "placed " + placed);
        for (int id : Kit.ids(s, EntityKind.PICKUP, 0)) {
            int cx = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
            int cy = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
            assertTrue(Math.max(Math.abs(cx - 1), Math.abs(cy - 1)) >= Placement.PAWN_CLEARANCE, "at " + cx + "," + cy);
        }
    }

    @Test
    void collectScores() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        int id = Kit.collectOne(match);
        assertFalse(match.state().exists(id));
        assertEquals(10, match.state().score(0));
    }

    @Test
    void respawnsAfterTwenty() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        assertEquals(3, Kit.count(s, EntityKind.PICKUP, 0));
        Kit.collectOne(match);
        assertEquals(2, Kit.count(s, EntityKind.PICKUP, 0));
        match.run(ScoreOrbPickup.RESPAWN_DELAY - 1, HeadlessMatch.InputScript.idle());
        assertEquals(2, Kit.count(s, EntityKind.PICKUP, 0), "the marker is still counting down");
        match.step();
        assertEquals(3, Kit.count(s, EntityKind.PICKUP, 0), "back 20 ticks after the collect");
    }

    @Test
    void neverRespawnsUnderClearRule() {
        ResolvedGame game = TestModules.resolvedOf(ScoreOrbPickup.INSTANCE,
                TestModules.rule(ModuleFlag.NEEDS_CLEARABLE));
        HeadlessMatch match = Kit.playing(game, 1, 4L);
        MatchState s = match.state();
        Kit.collectOne(match);
        assertEquals(10, s.score(0));
        match.run(200, HeadlessMatch.InputScript.idle());
        assertEquals(2, Kit.count(s, EntityKind.PICKUP, 0));
        assertEquals(0, Placement.countMarkers(s, 0));
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(game(), 1);
    }
}
