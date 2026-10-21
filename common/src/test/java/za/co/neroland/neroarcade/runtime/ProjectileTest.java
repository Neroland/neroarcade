package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;

/** 001 A2, step 8c: a seat's projectile against a hazard in its cell. */
class ProjectileTest {

    /** A hazard chip that cannot be shot. */
    private static final class Armoured extends RuntimeStubs.DriftHazard {
        Armoured() {
            super("hazard/armoured");
        }

        @Override
        public void onShot(ModuleContext ctx, int ownerSeat, int hazardEntityId, int projectileEntityId) {
        }
    }

    private static HeadlessMatch match() {
        return RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(new RuntimeStubs.DriftHazard("hazard/drift"),
                new Armoured(), RuntimeStubs.lives(3)), 1, 2L));
    }

    private static int shot(MatchState s) {
        return s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(8), RuntimeStubs.at(8), 0, 0);
    }

    @Test
    void twoShotsKillATwoHpHazard() {
        HeadlessMatch match = match();
        MatchState s = match.state();
        int hazard = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(8), RuntimeStubs.at(8), -1, 0);
        s.set(hazard, Entities.HP, 2);
        int first = shot(s);
        match.step();
        assertTrue(s.exists(hazard));
        assertEquals(1, s.get(hazard, Entities.HP));
        assertFalse(s.exists(first), "the projectile is spent");
        int second = shot(s);
        match.step();
        assertFalse(s.exists(hazard));
        assertFalse(s.exists(second));
        assertEquals(3, s.lives(0));
    }

    @Test
    void anUnshootableHazardSurvives() {
        HeadlessMatch match = match();
        MatchState s = match.state();
        int hazard = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(8), RuntimeStubs.at(8), -1, 1);
        int projectile = shot(s);
        match.step();
        assertTrue(s.exists(hazard));
        assertEquals(1, s.get(hazard, Entities.HP));
        assertFalse(s.exists(projectile), "the runtime still removes the projectile");
    }

    @Test
    void aPersistentProjectileFliesOn() {
        HeadlessMatch match = match();
        MatchState s = match.state();
        int hazard = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(8), RuntimeStubs.at(8), -1, 0);
        int projectile = shot(s);
        s.set(projectile, Entities.FLAGS, Entities.F_PERSISTENT);
        match.step();
        assertFalse(s.exists(hazard));
        assertTrue(s.exists(projectile));
    }
}
