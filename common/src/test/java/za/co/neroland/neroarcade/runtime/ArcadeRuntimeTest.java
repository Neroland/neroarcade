package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;

/** 001 A15 a–d: the runtime clarifications of the Stage 3 review (008b F1). */
class ArcadeRuntimeTest {

    /** A cursor whose {@code opponentInput} override counts its calls (test-only state; never shipped). */
    private static final class CountingPawn implements PawnModule {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Identifier id() {
            return TestModules.id("pawn/counting");
        }

        @Override
        public List<PropertySpec> properties() {
            return CursorPawn.INSTANCE.properties();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of();
        }

        @Override
        public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
            CursorPawn.INSTANCE.spawn(ctx, seat, cellX, cellY);
        }

        @Override
        public void applyInput(ModuleContext ctx, int seat, Intent intent) {
            CursorPawn.INSTANCE.applyInput(ctx, seat, intent);
        }

        @Override
        public void tick(ModuleContext ctx, int seat) {
            CursorPawn.INSTANCE.tick(ctx, seat);
        }

        @Override
        public SeatInput opponentInput(ModuleContext ctx, int seat) {
            calls.incrementAndGet();
            return SeatInput.NONE;
        }
    }

    /** A15.a: an override is asked on every simulated tick, not every OPPONENT_CADENCE. */
    @Test
    void overridingPawnIsAskedEveryTick() {
        CountingPawn pawn = new CountingPawn();
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(RuntimeStubs.lives(3, ModuleFlag.MULTI_SEAT_ONLY),
                TestChips.Versus.INSTANCE, pawn), 1, 5L);
        assertEquals(1, match.runtime().opponentSeat());
        assertTrue(!ArcadeRuntime.usesDefaultOpponent(pawn), "the override is detected");
        RuntimeStubs.playing(match);
        assertEquals(0, pawn.calls.get(), "never asked during the countdown");
        match.run(600, HeadlessMatch.InputScript.idle());
        assertEquals(600, pawn.calls.get());
    }

    /** A15.b: every housekeeping respawn grants SHIELD for max(remaining, 20) ticks. */
    @Test
    void respawnGrantsShieldGrace() {
        HeadlessMatch match = RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(RuntimeStubs.lives(5)), 1, 5L));
        MatchState s = match.state();
        assertEquals(0, s.statusTicks(0, SeatState.SHIELD), "no grace at the initial spawn");
        s.damageSeat(0, 1);
        assertEquals(Budgets.RESPAWN_TICKS, s.respawnTicks(0));
        match.run(Budgets.RESPAWN_TICKS, HeadlessMatch.InputScript.idle());
        assertTrue(s.pawnId(0) >= 0, "respawned");
        assertEquals(Budgets.RESPAWN_GRACE_TICKS, s.statusTicks(0, SeatState.SHIELD), "on the respawn tick");
        s.damageSeat(0, 1);
        assertEquals(4, s.lives(0), "the grace swallows a hit");
        match.run(Budgets.RESPAWN_GRACE_TICKS, HeadlessMatch.InputScript.idle());
        assertEquals(0, s.statusTicks(0, SeatState.SHIELD), "twenty ticks later");

        // an existing longer SHIELD is kept
        s.damageSeat(0, 1);
        assertEquals(3, s.lives(0));
        s.setStatus(0, SeatState.SHIELD, 100);
        match.run(Budgets.RESPAWN_TICKS, HeadlessMatch.InputScript.idle());
        assertTrue(s.pawnId(0) >= 0, "respawned again");
        assertEquals(100 - Budgets.RESPAWN_TICKS, s.statusTicks(0, SeatState.SHIELD), "the longer shield wins");
    }

    /** A15.c: passes 8, 8b and 8c skip F_HIDDEN entities. */
    @Test
    void hiddenEntitiesNeverCollide() {
        HeadlessMatch match = RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(RuntimeStubs.lives(3),
                new RuntimeStubs.DriftHazard("hazard/still")), 1, 5L));
        MatchState s = match.state();
        int pawn = s.pawnId(0);
        int px = s.get(pawn, Entities.X);
        int py = s.get(pawn, Entities.Y);

        int hazard = s.spawn(EntityKind.HAZARD, px, py, -1, 0);
        s.set(hazard, Entities.FLAGS, Entities.F_LETHAL | Entities.F_HIDDEN);
        int pickup = s.spawn(EntityKind.PICKUP, px, py, -1, 0);
        s.set(pickup, Entities.FLAGS, Entities.F_HIDDEN);
        int shot = s.spawn(EntityKind.PROJECTILE, px, py, 1, 0);
        s.set(shot, Entities.FLAGS, Entities.F_HIDDEN);
        s.setCell(8, 8, Cell.GOAL_0);
        int onGoal = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(8), RuntimeStubs.at(8), -1, 0);
        s.set(onGoal, Entities.FLAGS, Entities.F_LETHAL | Entities.F_HIDDEN);
        int target = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(10), RuntimeStubs.at(10), -1, 0);
        s.set(target, Entities.HP, 1);
        int hiddenShot = s.spawn(EntityKind.PROJECTILE, RuntimeStubs.at(10), RuntimeStubs.at(10), 1, 0);
        s.set(hiddenShot, Entities.FLAGS, Entities.F_HIDDEN);

        match.run(5, HeadlessMatch.InputScript.idle());
        assertEquals(3, s.lives(0), "a hidden hazard, enemy shot or goal-cell hazard costs nothing");
        assertTrue(s.exists(pickup), "a hidden pickup is not collected");
        assertTrue(s.exists(shot), "a hidden enemy projectile is ignored");
        assertTrue(s.exists(onGoal), "a hidden hazard on a goal cell scores nothing");
        assertTrue(s.exists(target) && s.exists(hiddenShot), "a hidden projectile hits no hazard");

        // the same hazard, shown, is a hit
        s.set(hazard, Entities.FLAGS, Entities.F_LETHAL);
        match.step();
        assertEquals(2, s.lives(0));
    }
}
