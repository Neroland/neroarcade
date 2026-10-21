package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.HitOutcome;

/** 001 A1: the pawn decides what a touch does; the runtime applies it. Pawn–pawn hits are directional. */
class HitOutcomeTest {

    private static HeadlessMatch solo(HitOutcome outcome) {
        return RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(new RuntimeStubs.OutcomePawn(outcome),
                new RuntimeStubs.DriftHazard("hazard/drift"), RuntimeStubs.lives(3)), 1, 1L));
    }

    /** A hazard one cell right of seat 0's pawn (cell 1,1), moving left onto it this tick. */
    private static int incomingHazard(MatchState s) {
        int id = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(2), RuntimeStubs.at(1), -1, 0);
        s.set(id, Entities.VX, -MatchState.FP);
        s.set(id, Entities.VY, 0);
        return id;
    }

    @Test
    void deflectKeepsLivesAndFlipsTheEntryComponent() {
        HeadlessMatch match = solo(HitOutcome.DEFLECT);
        MatchState s = match.state();
        int hazard = incomingHazard(s);
        match.step();
        assertEquals(3, s.lives(0));
        assertTrue(s.exists(s.pawnId(0)));
        assertTrue(s.exists(hazard));
        assertEquals(MatchState.FP, s.get(hazard, Entities.VX), "VX reflected");
        assertEquals(0, s.get(hazard, Entities.VY), "VY untouched");
        assertEquals(2, s.get(hazard, Entities.X) >> MatchState.FP_SHIFT, "moved back out of the pawn's cell");
    }

    @Test
    void damageCostsALife() {
        HeadlessMatch match = solo(HitOutcome.DAMAGE);
        incomingHazard(match.state());
        match.step();
        assertEquals(2, match.state().lives(0));
        assertEquals(-1, match.state().pawnId(0), "body removed until respawn");
    }

    @Test
    void ignoreDoesNothing() {
        HeadlessMatch match = solo(HitOutcome.IGNORE);
        MatchState s = match.state();
        int hazard = incomingHazard(s);
        match.step();
        assertEquals(3, s.lives(0));
        assertEquals(-MatchState.FP, s.get(hazard, Entities.VX));
        assertEquals(1, s.get(hazard, Entities.X) >> MatchState.FP_SHIFT);
    }

    @Test
    void aProjectileThatDamagesIsGoneNextTick() {
        HeadlessMatch match = solo(HitOutcome.DAMAGE);
        MatchState s = match.state();
        int shot = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(1), RuntimeStubs.at(1), 3, 0);
        match.step();
        assertEquals(2, s.lives(0));
        assertFalse(s.exists(shot));
    }

    @Test
    void aDeflectedProjectileIsReflectedAndChangesOwner() {
        HeadlessMatch match = solo(HitOutcome.DEFLECT);
        MatchState s = match.state();
        int shot = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(1), RuntimeStubs.at(1), 3, 0);
        s.set(shot, Entities.VY, -MatchState.FP);
        match.step();
        assertEquals(3, s.lives(0));
        assertTrue(s.exists(shot));
        assertEquals(0, s.get(shot, Entities.OWNER), "a returned shot belongs to the returner");
        assertEquals(MatchState.FP, s.get(shot, Entities.VY));
    }

    @Test
    void ownProjectileNeverHitsItsSeat() {
        HeadlessMatch match = solo(HitOutcome.DAMAGE);
        MatchState s = match.state();
        int shot = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(1), RuntimeStubs.at(1), 0, 0);
        match.step();
        assertEquals(3, s.lives(0));
        assertTrue(s.exists(shot));
    }

    private static HeadlessMatch serpents() {
        return RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(
                new RuntimeStubs.OutcomePawn(HitOutcome.DAMAGE, ModuleFlag.LETHAL_TO_PAWNS),
                TestModules.multiplayer(false), RuntimeStubs.lives(3)), 2, 1L));
    }

    @Test
    void onlyTheHeadThatEntersABodyIsHit() {
        HeadlessMatch match = serpents();
        MatchState s = match.state();
        s.spawn(EntityKind.SEGMENT, RuntimeStubs.at(5), RuntimeStubs.at(5), 1, 0);
        int head = s.pawnId(0);
        s.set(head, Entities.X, RuntimeStubs.at(5));
        s.set(head, Entities.Y, RuntimeStubs.at(5));
        match.step();
        assertEquals(2, s.lives(0), "seat 0 ran into seat 1's body");
        assertEquals(3, s.lives(1), "the body's owner is untouched");
    }

    @Test
    void headOnHitsBoth() {
        HeadlessMatch match = serpents();
        MatchState s = match.state();
        for (int seat = 0; seat < 2; seat++) {
            int head = s.pawnId(seat);
            s.set(head, Entities.X, RuntimeStubs.at(7));
            s.set(head, Entities.Y, RuntimeStubs.at(7));
        }
        match.step();
        assertEquals(2, s.lives(0));
        assertEquals(2, s.lives(1));
    }
}
