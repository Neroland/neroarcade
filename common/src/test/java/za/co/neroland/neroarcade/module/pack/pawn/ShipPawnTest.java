package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/ship}. */
class ShipPawnTest {

    private static ResolvedGame game(String drag) throws ValidationError {
        ResolvedGame g = TestModules.resolvedOf(ShipPawn.INSTANCE, TestModules.rule());
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(),
                g.pawnProps().with("drag", new PropertyValue.EnumValue(drag)), g.rule(), g.ruleProps(), g.hazards(),
                g.hazardProps(), g.pickups(), g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(),
                g.modifier(), g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    private static HeadlessMatch playing(ResolvedGame game) {
        return HeadlessMatch.of(game, 1, 1L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    /** The ship at (cx, cy), at rest, facing right. */
    private static void put(MatchState s, int cx, int cy) {
        int ship = s.pawnId(0);
        s.set(ship, Entities.X, centre(cx));
        s.set(ship, Entities.Y, centre(cy));
        s.set(ship, Entities.VX, 0);
        s.set(ship, Entities.VY, 0);
        s.setAux(ship, GridStep.withDir(s.aux(ship), GridStep.RIGHT));
    }

    @Test
    void inertiaCoasts() throws ValidationError {
        HeadlessMatch match = playing(game("none"));
        MatchState s = match.state();
        put(s, 3, 8);
        for (int i = 0; i < 3; i++) {
            match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        }
        int ship = s.pawnId(0);
        int v = s.get(ship, Entities.VX);
        assertEquals(3 * 3 * 8, v, "thrust 3 adds 24 units/tick each tick");
        int x = s.get(ship, Entities.X);
        match.step();
        assertEquals(x + v, s.get(ship, Entities.X), "no input: it keeps moving");
        assertEquals(v, s.get(ship, Entities.VX), "and without drag it keeps its speed");
    }

    @Test
    void dragStops() throws ValidationError {
        HeadlessMatch match = playing(game("heavy"));
        MatchState s = match.state();
        put(s, 3, 8);
        for (int i = 0; i < 6; i++) {
            match.step(SeatInput.of(SeatInput.RIGHT, 0, 0));
        }
        int ship = s.pawnId(0);
        assertTrue(s.get(ship, Entities.VX) > 0);
        for (int i = 0; i < 40; i++) {
            match.step();
        }
        assertEquals(0, s.get(ship, Entities.VX), "heavy drag brings it to rest within 40 ticks");
        assertTrue((s.get(ship, Entities.X) >> MatchState.FP_SHIFT) < 14, "by drag, not by the wall");
        assertEquals(6, ShipPawn.drag(7, 3), "below 8 heavy drag still takes one unit a tick");
        assertEquals(0, ShipPawn.drag(1, 3), "so the last units go too");
        assertEquals(-6, ShipPawn.drag(-7, 3));
        assertEquals(7, ShipPawn.drag(7, -1), "none");
    }

    @Test
    void firesWithCooldown() throws ValidationError {
        HeadlessMatch match = playing(game("light"));
        MatchState s = match.state();
        put(s, 1, 8);
        TreeSet<Integer> seen = new TreeSet<>();
        for (int i = 0; i < 30; i++) {
            match.step(SeatInput.of(SeatInput.A, 0, 0));
            s.forEach(EntityKind.PROJECTILE, seen::add);
        }
        assertEquals(3, seen.size(), "A held 30 ticks: shots at ticks 0, 10 and 20");
    }

    @Test
    void projectileRemovedAtWall() throws ValidationError {
        HeadlessMatch match = playing(game("light"));
        MatchState s = match.state();
        put(s, 1, 8);
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertEquals(1, s.count(EntityKind.PROJECTILE));
        int[] shot = {-1};
        s.forEach(EntityKind.PROJECTILE, id -> shot[0] = id);
        assertEquals(40, s.get(shot[0], Entities.TTL) + 1, "TTL 40, one tick spent");
        assertEquals(MatchState.FP, s.get(shot[0], Entities.VX), "one cell per tick along the facing");
        assertEquals(2, s.get(shot[0], Entities.X) >> MatchState.FP_SHIFT);
        for (int i = 0; i < 14; i++) {
            match.step();
        }
        assertFalse(s.exists(shot[0]), "gone at the border wall, long before its TTL");
    }

    @Test
    void shotKillsChaserViaOnShot() throws ValidationError {
        HeadlessMatch match = playing(game("light"));
        MatchState s = match.state();
        put(s, 1, 8);
        int target = s.spawn(EntityKind.HAZARD, centre(5), centre(8), -1, 0);
        s.set(target, Entities.HP, 1);
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertTrue(s.exists(target));
        for (int i = 0; i < 4; i++) {
            match.step();
        }
        assertFalse(s.exists(target), "the shot reached it and onShot took its last hp");
        assertEquals(0, s.count(EntityKind.PROJECTILE), "the shot is spent");
        assertTrue(s.seatAlive(0));
    }

    /** 001 A15.d: a dead ship's shots vanish with it; another seat's shots stay. */
    @Test
    void deathClearsOwnShots() throws ValidationError {
        HeadlessMatch match = playing(game("light"));
        MatchState s = match.state();
        put(s, 1, 8);
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        assertTrue(s.count(EntityKind.PROJECTILE) > 0, "fired");
        int other = s.spawn(EntityKind.PROJECTILE, centre(12), centre(12), 1, 0);
        s.setLives(0, 3);
        s.damageSeat(0, 1);
        assertEquals(1, s.count(EntityKind.PROJECTILE), "only the other seat's shot is left");
        assertTrue(s.exists(other));
    }

    @Test
    void firesAlongItsNewFacing() throws ValidationError {
        HeadlessMatch match = playing(game("light"));
        MatchState s = match.state();
        put(s, 8, 8);
        match.step(SeatInput.of(SeatInput.A | SeatInput.DOWN, 0, 0));
        int[] shot = {-1};
        s.forEach(EntityKind.PROJECTILE, id -> shot[0] = id);
        assertNotEquals(-1, shot[0]);
        assertEquals(MatchState.FP, s.get(shot[0], Entities.VY), "it fires along its new facing (down)");
        assertEquals(0, s.get(shot[0], Entities.VX));
    }

    @Test
    void replayStable() throws ValidationError {
        long a = HeadlessMatch.of(game("light"), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game("light"), 1, 3L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
