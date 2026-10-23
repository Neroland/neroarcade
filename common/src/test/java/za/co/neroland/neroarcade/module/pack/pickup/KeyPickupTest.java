package za.co.neroland.neroarcade.module.pack.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class KeyPickupTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(KeyPickup.INSTANCE);
    }

    @Test
    void placedFourClearOfTheSpawn() {
        MatchState s = Kit.playing(Kit.with(game(), KeyPickup.INSTANCE, "count", 4), 1, 9L).state();
        for (int id : Kit.ids(s, EntityKind.PICKUP, 0)) {
            int cx = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
            int cy = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
            assertTrue(Math.max(Math.abs(cx - 1), Math.abs(cy - 1)) >= KeyPickup.PAWN_CLEARANCE);
        }
    }

    @Test
    void neverRespawns() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        assertEquals(1, Kit.count(s, EntityKind.PICKUP, 0));
        Kit.collectOne(match);
        match.run(300, HeadlessMatch.InputScript.idle());
        assertEquals(0, Kit.count(s, EntityKind.PICKUP, 0));
        assertEquals(0, Placement.countMarkers(s, 0));
    }

    @Test
    void scoresFiveWithoutKeyRule() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        Kit.collectOne(match);
        assertEquals(KeyPickup.ORB_VALUE, match.state().score(0));
    }

    @Test
    void silentWithKeyRule() {
        ResolvedGame game = TestModules.resolvedOf(KeyPickup.INSTANCE,
                TestModules.rule(ModuleFlag.PLACES_EXIT, ModuleFlag.NEEDS_KEY));
        HeadlessMatch match = Kit.playing(game, 1, 4L);
        Kit.collectOne(match);
        assertEquals(0, match.state().score(0));
        assertEquals(0, Kit.count(match.state(), EntityKind.PICKUP, 0), "collected and gone: the rule sees no key");
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(game(), 1);
    }
}
