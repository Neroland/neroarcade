package za.co.neroland.neroarcade.runtime;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.HitOutcome;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;

/** Small chips for driving the collision, goal and opponent passes from tests. Pure, stateless. */
final class RuntimeStubs {

    private RuntimeStubs() {
    }

    /** Fixed-point centre of a cell. */
    static int at(int cell) {
        return (cell << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    /** Runs a started match through its countdown so the next step simulates tick 0. */
    static HeadlessMatch playing(HeadlessMatch match) {
        return match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    /** A cursor pawn whose {@code onHit} returns a fixed outcome. */
    static final class OutcomePawn implements PawnModule {
        private final HitOutcome outcome;
        private final Set<ModuleFlag> flags;

        OutcomePawn(HitOutcome outcome, ModuleFlag... flags) {
            this.outcome = outcome;
            this.flags = Set.of(flags);
        }

        @Override
        public Identifier id() {
            return TestModules.id("pawn/outcome");
        }

        @Override
        public List<PropertySpec> properties() {
            return CursorPawn.INSTANCE.properties();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return flags;
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
        public HitOutcome onHit(ModuleContext ctx, int seat, int entityId) {
            return outcome;
        }
    }

    /** A hazard chip that spawns nothing and moves its entities by their velocity; tests place them. */
    static class DriftHazard implements HazardModule {
        private final String path;

        DriftHazard(String path) {
            this.path = path;
        }

        @Override
        public Identifier id() {
            return TestModules.id(path);
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
            s.forEach(EntityKind.HAZARD, id -> {
                if (s.tag(id) == ctx.stackIndex()) {
                    s.set(id, Entities.X, s.wrapX(s.get(id, Entities.X) + s.get(id, Entities.VX)));
                    s.set(id, Entities.Y, s.wrapY(s.get(id, Entities.Y) + s.get(id, Entities.VY)));
                }
            });
        }
    }

    /** A rule that gives every seat {@code lives} and never decides. */
    static RuleModule lives(int lives, ModuleFlag... flags) {
        return new RuleModule() {
            @Override
            public Identifier id() {
                return TestModules.id("rule/lives");
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
                for (int seat = 0; seat < ctx.state().seatCount(); seat++) {
                    ctx.state().setLives(seat, lives);
                }
            }

            @Override
            public RuleOutcome evaluate(ModuleContext ctx) {
                return RuleOutcome.CONTINUE;
            }
        };
    }
}
