package za.co.neroland.neroarcade.module.pack.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.pickup.PickupPackTest.Kit;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatState;

class SpeedBoostPickupTest {

    private static ResolvedGame game() {
        return TestModules.resolvedOf(SpeedBoostPickup.INSTANCE);
    }

    @Test
    void setsSpeedStatusForDuration() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        Kit.collectOne(match);
        assertTrue((s.statusFlags(0) & SeatState.SPEED) != 0);
        assertEquals(99, s.statusTicks(0, SeatState.SPEED), "set to 100, then one housekeeping pass");
        match.run(98, HeadlessMatch.InputScript.idle());
        assertTrue((s.statusFlags(0) & SeatState.SPEED) != 0);
        match.step();
        assertEquals(0, s.statusFlags(0) & SeatState.SPEED, "gone after 100 ticks");
    }

    @Test
    void respawnsAfterHundred() {
        HeadlessMatch match = Kit.playing(game(), 1, 4L);
        MatchState s = match.state();
        assertEquals(1, Kit.count(s, EntityKind.PICKUP, 0));
        Kit.collectOne(match);
        assertEquals(0, Kit.count(s, EntityKind.PICKUP, 0));
        match.run(SpeedBoostPickup.RESPAWN_DELAY - 1, HeadlessMatch.InputScript.idle());
        assertEquals(0, Kit.count(s, EntityKind.PICKUP, 0));
        match.step();
        assertEquals(1, Kit.count(s, EntityKind.PICKUP, 0));
    }

    @Test
    void replayStable() {
        Kit.assertReplayStable(game(), 1);
    }
}
