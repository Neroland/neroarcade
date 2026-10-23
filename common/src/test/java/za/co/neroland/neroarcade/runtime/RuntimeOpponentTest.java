package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.HitOutcome;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.pack.hazard.ShrinkingFloorHazard;

/**
 * 001 A5: the runtime opponent's input comes from the PAWN chip ({@code opponentInput}), default
 * {@link RuntimeOpponent#greedy}. Minimum quality: it collects, it flees, and a paddle that overrides it defends.
 */
class RuntimeOpponentTest {

    /** Seat 0 is an idle human on grid_16; seat 1 is the runtime opponent at (14, 14). */
    private static HeadlessMatch versus(ArcadeModule extra) {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(RuntimeStubs.lives(3, ModuleFlag.MULTI_SEAT_ONLY),
                TestChips.Versus.INSTANCE, extra), 1, 9L);
        assertEquals(1, match.runtime().opponentSeat());
        return match;
    }

    /**
     * One orb at a fixed cell that never respawns. (5, 8) lies on the old hop lattice of a speed-3 cursor from
     * (14, 14); (4, 10) does not, and oscillated before the greedy re-steered every tick (PROGRESS 2026-10-21).
     */
    private static final class OneOrb implements PickupModule {
        private final int cx;
        private final int cy;

        OneOrb(int cx, int cy) {
            this.cx = cx;
            this.cy = cy;
        }

        @Override
        public Identifier id() {
            return TestModules.id("pickup/one_orb");
        }

        @Override
        public List<PropertySpec> properties() {
            return List.of();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.SCORES);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            ctx.state().spawn(EntityKind.PICKUP, RuntimeStubs.at(cx), RuntimeStubs.at(cy), -1, ctx.stackIndex());
        }

        @Override
        public void tick(ModuleContext ctx) {
        }

        @Override
        public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
            ctx.state().addScore(seat, 10);
        }
    }

    @Test
    void collectsALoneOrbWithin160Ticks() {
        assertCollected(5, 8);
    }

    /** The orb that used to sit between two hops: the target is fixed for 4 ticks, the direction is not. */
    @Test
    void collectsAnOrbOffTheOldHopLattice() {
        assertCollected(4, 10);
    }

    @Test
    void targetAndSteerArePureAndComposeToGreedy() {
        HeadlessMatch match = RuntimeStubs.playing(versus(new OneOrb(4, 10)));
        match.run(7, HeadlessMatch.InputScript.idle());
        MatchState s = match.state();
        long before = s.stateHash();
        ModuleContext ctx = new ModuleContext(s, null, Rng.view(s), match.runtime().game(), s.tick(), 0);
        int target = RuntimeOpponent.target(ctx, 1);
        assertEquals(target, RuntimeOpponent.target(ctx, 1));
        assertEquals(RuntimeOpponent.steer(ctx, 1, target), RuntimeOpponent.greedy(ctx, 1));
        assertEquals(before, s.stateHash(), "deciding never mutates the state");
        assertEquals(SeatInput.NONE, RuntimeOpponent.steer(ctx, 1, RuntimeOpponent.NO_TARGET));
    }

    private static void assertCollected(int cx, int cy) {
        HeadlessMatch match = RuntimeStubs.playing(versus(new OneOrb(cx, cy)));
        MatchState s = match.state();
        int ticks = 0;
        while (s.count(EntityKind.PICKUP) > 0 && ticks < 160) {
            match.step();
            ticks++;
        }
        assertEquals(0, s.count(EntityKind.PICKUP), "orb still on the board after 160 ticks");
        assertEquals(10, s.score(1), "the opponent collected it");
        assertEquals(0, s.score(0));
    }

    @Test
    void fleesAnAdjacentLethalHazardWithin12Ticks() {
        HeadlessMatch match = versus(new RuntimeStubs.DriftHazard("hazard/still"));
        MatchState s = match.state();
        s.spawn(EntityKind.HAZARD, RuntimeStubs.at(13), RuntimeStubs.at(14), -1, 0);
        RuntimeStubs.playing(match);
        match.run(12, HeadlessMatch.InputScript.idle());
        int pawn = s.pawnId(1);
        assertTrue(pawn >= 0, "the opponent was never hit");
        int dx = Math.abs((s.get(pawn, Entities.X) >> MatchState.FP_SHIFT) - 13);
        int dy = Math.abs((s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT) - 14);
        assertTrue(Math.max(dx, dy) >= 3, "Chebyshev distance " + Math.max(dx, dy));
        assertEquals(3, s.lives(1));
    }

    /**
     * 008b Q7: with nothing to flee, collect or hunt, the opponent heads for the centre and never steps into VOID.
     * The outer floor ring is void (seat 1 starts one ring in, at (13, 13)); then a real shrinking floor.
     */
    @Test
    void wandererSeeksTheCentreAndAvoidsVoid() {
        HeadlessMatch match = versus(TestModules.pawn());
        MatchState s = match.state();
        for (int i = 1; i <= 14; i++) {
            for (int[] c : new int[][] {{i, 1}, {i, 14}, {1, i}, {14, i}}) {
                if (!(c[0] == 1 && c[1] == 1)) {
                    s.setCell(c[0], c[1], Cell.VOID);
                }
            }
        }
        int pawn = s.pawnId(1);
        s.set(pawn, Entities.X, RuntimeStubs.at(13));
        s.set(pawn, Entities.Y, RuntimeStubs.at(13));
        RuntimeStubs.playing(match);
        boolean near = false;
        for (int t = 0; t < 60 && !near; t++) {
            match.step();
            int p = s.pawnId(1);
            assertTrue(p >= 0, "the wanderer walked into the void at tick " + t);
            int dx = Math.abs((s.get(p, Entities.X) >> MatchState.FP_SHIFT) - 8);
            int dy = Math.abs((s.get(p, Entities.Y) >> MatchState.FP_SHIFT) - 8);
            near = Math.max(dx, dy) <= 2;
        }
        assertTrue(near, "within 2 cells of the centre within 60 ticks");
        assertEquals(3, s.lives(1));

        HeadlessMatch island = versus(ShrinkingFloorHazard.INSTANCE);
        RuntimeStubs.playing(island).run(1200, HeadlessMatch.InputScript.idle());
        assertEquals(3, island.state().lives(1), "interval 100 / min_size 4: the opponent rides the island");
    }

    /** 008b Q7: an AUTO_MOVE pawn heading away from the centre turns (ccw, else cw) instead of reversing. */
    @Test
    void autoMoverTurnsInsteadOfReversing() {
        for (boolean autoMove : new boolean[] {true, false}) {
            PawnModule stub = autoMove ? TestModules.pawn(ModuleFlag.AUTO_MOVE) : TestModules.pawn();
            HeadlessMatch match = RuntimeStubs.playing(HeadlessMatch.of(TestModules.resolvedOf(stub), 1, 4L));
            MatchState s = match.state();
            int pawn = s.pawnId(0);
            s.set(pawn, Entities.X, RuntimeStubs.at(12));
            s.set(pawn, Entities.Y, RuntimeStubs.at(8));
            s.set(pawn, Entities.VX, 64);
            s.set(pawn, Entities.VY, 0);
            ModuleContext ctx = new ModuleContext(s, null, Rng.view(s), match.runtime().game(), s.tick(), 0);
            if (!autoMove) {
                assertEquals(SeatInput.of(SeatInput.LEFT, 0, 0), RuntimeOpponent.greedy(ctx, 0),
                        "a free mover simply heads back to the centre");
                s.set(pawn, Entities.X, RuntimeStubs.at(8));
                assertEquals(SeatInput.NONE, RuntimeOpponent.greedy(ctx, 0), "and stands at the centre");
                continue;
            }
            assertEquals(SeatInput.of(SeatInput.UP, 0, 0), RuntimeOpponent.greedy(ctx, 0),
                    "heading right, the centre is behind: turn ccw (up)");
            s.setCell(12, 7, Cell.WALL);
            assertEquals(SeatInput.of(SeatInput.DOWN, 0, 0), RuntimeOpponent.greedy(ctx, 0), "ccw blocked: cw");
            s.set(pawn, Entities.X, RuntimeStubs.at(8));
            assertEquals(SeatInput.of(SeatInput.RIGHT, 0, 0), RuntimeOpponent.greedy(ctx, 0),
                    "at the centre an AUTO_MOVE pawn holds its travel direction");
        }
    }

    @Test
    void aPaddleOpponentConcedesFewerThanThreeIn600Ticks() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(RuntimeStubs.lives(9, ModuleFlag.MULTI_SEAT_ONLY),
                TestChips.Versus.INSTANCE, new Paddle(), new Ball()), 1, 21L);
        RuntimeStubs.playing(match).run(600, HeadlessMatch.InputScript.idle());
        MatchState s = match.state();
        assertTrue(s.lives(0) < 9, "the idle seat concedes, so goals do happen: " + s.lives(0));
        assertTrue(9 - s.lives(1) < 3, "the paddle opponent conceded " + (9 - s.lives(1)));
    }

    /**
     * A horizontal three-cell paddle (stand-in for Stage 3's {@code pawn/paddle}): seat 0 on row h-3 with its
     * goal line on h-2, seat 1 on row 2 with its goal line on row 1. DEFLECTs every hit; its opponent moves
     * toward the nearest ball's projected crossing of its row.
     */
    static final class Paddle implements PawnModule {
        private static final int SPEED = 192;

        @Override
        public Identifier id() {
            return TestModules.id("pawn/paddle_stub");
        }

        @Override
        public List<PropertySpec> properties() {
            return List.of();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of();
        }

        private static int row(MatchState s, int seat) {
            return seat == 0 ? s.height() - 3 : 2;
        }

        @Override
        public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
            MatchState s = ctx.state();
            int goalRow = seat == 0 ? s.height() - 2 : 1;
            for (int x = 1; x < s.width() - 1; x++) {
                s.setCell(x, goalRow, Cell.goal(seat));
            }
            int x = Math.max(2, Math.min(s.width() - 3, cellX));
            int y = row(s, seat);
            int id = s.spawn(EntityKind.PAWN, RuntimeStubs.at(x), RuntimeStubs.at(y), seat, 0);
            s.setPawnId(seat, id);
            for (int off = -1; off <= 1; off += 2) {
                int seg = s.spawn(EntityKind.SEGMENT, RuntimeStubs.at(x + off), RuntimeStubs.at(y), seat, 0);
                s.setAux(seg, (off + 1) << 2);
            }
        }

        @Override
        public void applyInput(ModuleContext ctx, int seat, Intent intent) {
            ctx.state().set(ctx.state().pawnId(seat), Entities.VX, intent.moveX() * SPEED);
        }

        @Override
        public void tick(ModuleContext ctx, int seat) {
            MatchState s = ctx.state();
            int id = s.pawnId(seat);
            int x = Math.max(RuntimeStubs.at(2), Math.min(RuntimeStubs.at(s.width() - 3),
                    s.get(id, Entities.X) + s.get(id, Entities.VX)));
            s.set(id, Entities.X, x);
            s.forEach(EntityKind.SEGMENT, seg -> {
                if (s.get(seg, Entities.OWNER) == seat) {
                    s.set(seg, Entities.X, x + ((s.aux(seg) >> 2) - 1) * MatchState.FP);
                }
            });
        }

        @Override
        public HitOutcome onHit(ModuleContext ctx, int seat, int entityId) {
            return HitOutcome.DEFLECT;
        }

        @Override
        public SeatInput opponentInput(ModuleContext ctx, int seat) {
            MatchState s = ctx.state();
            int pawn = s.pawnId(seat);
            int rowY = RuntimeStubs.at(row(s, seat));
            int target = RuntimeStubs.at(s.width() / 2);
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < s.entityCount(); i++) {
                int id = s.entityIdAt(i);
                if (s.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal()) {
                    continue;
                }
                int y = s.get(id, Entities.Y);
                int vy = s.get(id, Entities.VY);
                if (vy == 0 || Integer.signum(rowY - y) != Integer.signum(vy)) {
                    continue;
                }
                int t = (rowY - y) / vy;
                if (t < best) {
                    best = t;
                    target = fold(s.get(id, Entities.X) + s.get(id, Entities.VX) * t, MatchState.FP,
                            (s.width() - 1) * MatchState.FP);
                }
            }
            int dx = target - s.get(pawn, Entities.X);
            if (Math.abs(dx) <= MatchState.FP) {
                return SeatInput.NONE;
            }
            return SeatInput.of(dx > 0 ? SeatInput.RIGHT : SeatInput.LEFT, 0, 0);
        }

        /** Reflects a projected coordinate back into {@code [lo, hi]} as wall bounces would. */
        private static int fold(int value, int lo, int hi) {
            int span = hi - lo;
            int p = Math.floorMod(value - lo, 2 * span);
            return lo + (p > span ? 2 * span - p : p);
        }
    }

    /** One ball, served from the centre whenever none is on the board; bounces off walls only. */
    static final class Ball implements HazardModule {
        @Override
        public Identifier id() {
            return TestModules.id("hazard/ball_stub");
        }

        @Override
        public List<PropertySpec> properties() {
            return List.of();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.LETHAL);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
        }

        @Override
        public void tick(ModuleContext ctx) {
            MatchState s = ctx.state();
            int[] mine = {0};
            s.forEach(EntityKind.HAZARD, id -> {
                if (s.tag(id) != ctx.stackIndex()) {
                    return;
                }
                mine[0]++;
                int x = s.get(id, Entities.X);
                int y = s.get(id, Entities.Y);
                int vx = s.get(id, Entities.VX);
                int vy = s.get(id, Entities.VY);
                if (Cell.blocks(s.cell((x + vx) >> MatchState.FP_SHIFT, y >> MatchState.FP_SHIFT))) {
                    vx = -vx;
                }
                if (Cell.blocks(s.cell(x >> MatchState.FP_SHIFT, (y + vy) >> MatchState.FP_SHIFT))) {
                    vy = -vy;
                }
                s.set(id, Entities.VX, vx);
                s.set(id, Entities.VY, vy);
                s.set(id, Entities.X, x + vx);
                s.set(id, Entities.Y, y + vy);
            });
            if (mine[0] == 0) {
                int id = s.spawn(EntityKind.HAZARD, RuntimeStubs.at(s.width() / 2), RuntimeStubs.at(s.height() / 2), -1,
                        ctx.stackIndex());
                if (id >= 0) {
                    s.set(id, Entities.VX, ctx.rng().chance(1, 2) ? 192 : -192);
                    s.set(id, Entities.VY, ctx.rng().chance(1, 2) ? 128 : -128);
                }
            }
        }
    }
}
