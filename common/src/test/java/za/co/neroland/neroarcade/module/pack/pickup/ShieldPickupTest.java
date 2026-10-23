package za.co.neroland.neroarcade.module.pack.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.pickup.PickupPackTest.Kit;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatState;

class ShieldPickupTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(ShieldPickup.INSTANCE);
    }

    @Test
    void shieldAbsorbsHit() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        Kit.collectOne(match);
        assertTrue((s.statusFlags(0) & SeatState.SHIELD) != 0);
        Kit.hazardOnPawn(s);
        match.run(10, HeadlessMatch.InputScript.idle());
        assertTrue(s.seatAlive(0), "ten hazard contacts, no life lost");
        assertEquals(1, s.lives(0));
    }

    @Test
    void expires() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        Kit.collectOne(match);
        match.run(99, HeadlessMatch.InputScript.idle());
        assertEquals(0, s.statusFlags(0) & SeatState.SHIELD, "100 ticks, then gone");
        Kit.hazardOnPawn(s);
        match.step();
        assertFalse(s.seatAlive(0), "the next touch costs the only life");
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(game(), 1);
    }
}
