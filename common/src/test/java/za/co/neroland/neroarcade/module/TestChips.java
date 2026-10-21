package za.co.neroland.neroarcade.module;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * Test-scope chips that exercise every slot kind the shipped defaults do not: the two stack slots, a scoring
 * rule, a modifier and two multi-seat chips. Behaviour is deliberately simple but real (motion, bounces,
 * collection, scoring), so replays and snapshots have something to disagree about. Stateless and pure, like
 * shipped chips.
 */
public final class TestChips {

    private TestChips() {
    }

    static Identifier ident(String path) {
        return Identifier.fromNamespaceAndPath("neroarcade", path);
    }

    /** A random EMPTY cell from the match RNG (bounded attempts, then the first empty cell). */
    static int[] emptyCell(ModuleContext ctx) {
        MatchState s = ctx.state();
        for (int attempt = 0; attempt < 32; attempt++) {
            int x = ctx.rng().nextInt(s.width());
            int y = ctx.rng().nextInt(s.height());
            if (s.cell(x, y) == Cell.EMPTY) {
                return new int[] {x, y};
            }
        }
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                if (s.cell(x, y) == Cell.EMPTY) {
                    return new int[] {x, y};
                }
            }
        }
        return new int[] {1, 1};
    }

    /** {@code hazard/test_bouncer}: {@code count} balls bouncing diagonally off walls. LETHAL. */
    public static final class Bouncer implements HazardModule {
        public static final Bouncer INSTANCE = new Bouncer();
        private static final List<PropertySpec> PROPS = List.of(
                PropertySpec.integer("count", 1, 1, 4), PropertySpec.integer("speed", 3, 1, 5));

        @Override
        public Identifier id() {
            return ident("hazard/test_bouncer");
        }

        @Override
        public List<PropertySpec> properties() {
            return PROPS;
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.LETHAL);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            int speed = ctx.props().getInt("speed") * 64;
            for (int i = 0; i < ctx.props().getInt("count"); i++) {
                int[] c = emptyCell(ctx);
                int id = ctx.state().spawn(EntityKind.HAZARD, (c[0] << 8) + 128, (c[1] << 8) + 128, -1, ctx.stackIndex());
                if (id >= 0) {
                    ctx.state().set(id, Entities.VX, ctx.rng().chance(1, 2) ? speed : -speed);
                    ctx.state().set(id, Entities.VY, ctx.rng().chance(1, 2) ? speed : -speed);
                    ctx.state().set(id, Entities.FLAGS, Entities.F_LETHAL);
                }
            }
        }

        @Override
        public void tick(ModuleContext ctx) {
            MatchState s = ctx.state();
            s.forEach(EntityKind.HAZARD, id -> {
                if (s.tag(id) != ctx.stackIndex()) {
                    return;
                }
                int x = s.get(id, Entities.X);
                int y = s.get(id, Entities.Y);
                int vx = s.get(id, Entities.VX);
                int vy = s.get(id, Entities.VY);
                if (Cell.blocks(s.cell((x + vx) >> 8, y >> 8))) {
                    vx = -vx;
                }
                if (Cell.blocks(s.cell(x >> 8, (y + vy) >> 8))) {
                    vy = -vy;
                }
                if (!Cell.blocks(s.cell((x + vx) >> 8, (y + vy) >> 8))) {
                    x = s.wrapX(x + vx);
                    y = s.wrapY(y + vy);
                }
                s.set(id, Entities.X, x);
                s.set(id, Entities.Y, y);
                s.set(id, Entities.VX, vx);
                s.set(id, Entities.VY, vy);
            });
        }
    }

    /** {@code pickup/test_orb}: keeps {@code count} orbs on the board; each is worth {@code value}. */
    public static final class Orb implements PickupModule {
        public static final Orb INSTANCE = new Orb();
        private static final List<PropertySpec> PROPS = List.of(
                PropertySpec.integer("value", 10, 1, 100), PropertySpec.integer("count", 3, 1, 8));

        @Override
        public Identifier id() {
            return ident("pickup/test_orb");
        }

        @Override
        public List<PropertySpec> properties() {
            return PROPS;
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.SCORES, ModuleFlag.CLEARABLE);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            tick(ctx);
        }

        @Override
        public void tick(ModuleContext ctx) {
            MatchState s = ctx.state();
            int[] mine = {0};
            s.forEach(EntityKind.PICKUP, id -> mine[0] += s.tag(id) == ctx.stackIndex() ? 1 : 0);
            for (int i = mine[0]; i < ctx.props().getInt("count"); i++) {
                int[] c = emptyCell(ctx);
                s.spawn(EntityKind.PICKUP, (c[0] << 8) + 128, (c[1] << 8) + 128, -1, ctx.stackIndex());
            }
        }

        @Override
        public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
            ctx.state().addScore(seat, ctx.props().getInt("value"));
        }
    }

    /** {@code rule/test_score}: win at {@code target} points; lose when every seat is out. NEEDS_SCORE. */
    public static final class ScoreRule implements RuleModule {
        public static final ScoreRule INSTANCE = new ScoreRule();
        private static final List<PropertySpec> PROPS = List.of(
                PropertySpec.integer("target", 100, 1, 9999), PropertySpec.integer("lives", 3, 1, 9));

        @Override
        public Identifier id() {
            return ident("rule/test_score");
        }

        @Override
        public List<PropertySpec> properties() {
            return PROPS;
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.NEEDS_SCORE);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            for (int seat = 0; seat < ctx.state().seatCount(); seat++) {
                ctx.state().setLives(seat, ctx.props().getInt("lives"));
            }
        }

        @Override
        public RuleOutcome evaluate(ModuleContext ctx) {
            MatchState s = ctx.state();
            int winners = 0;
            for (int seat = 0; seat < s.seatCount(); seat++) {
                if (s.score(seat) >= ctx.props().getInt("target")) {
                    winners |= 1 << seat;
                }
            }
            if (winners != 0) {
                s.setWinners(winners);
                return RuleOutcome.WIN;
            }
            if (s.aliveSeatMask() == 0) {
                s.setWinners(0);
                return RuleOutcome.LOSE;
            }
            return RuleOutcome.CONTINUE;
        }
    }

    /** {@code modifier/test_mirror}: flips horizontal movement. */
    public static final class Mirror implements ModifierModule {
        public static final Mirror INSTANCE = new Mirror();

        @Override
        public Identifier id() {
            return ident("modifier/test_mirror");
        }

        @Override
        public List<PropertySpec> properties() {
            return List.of();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of();
        }

        @Override
        public Intent transformIntent(ModuleContext ctx, int seat, Intent in) {
            return new Intent(-in.moveX(), in.moveY(), -in.aimX(), in.aimY(), in.a(), in.b(), in.aPressed(), in.bPressed());
        }
    }

    /** {@code multiplayer/test_coop}: 2–4 seats on one board, optionally one life pool. */
    public static final class Coop implements MultiplayerModule {
        public static final Coop INSTANCE = new Coop();
        private static final List<PropertySpec> PROPS = List.of(
                PropertySpec.integer("seats", 2, 2, 4), PropertySpec.bool("shared_lives", true));

        @Override
        public Identifier id() {
            return ident("multiplayer/test_coop");
        }

        @Override
        public List<PropertySpec> properties() {
            return PROPS;
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of();
        }

        @Override
        public int seats(PropertyValues props) {
            return props.getInt("seats");
        }

        @Override
        public SeatLayout layout(ModuleContext ctx) {
            return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
        }

        @Override
        public boolean sharesLives(PropertyValues props) {
            return props.getBool("shared_lives");
        }

        @Override
        public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
            return fromRule;
        }
    }

    /** {@code multiplayer/test_versus}: two seats; a runtime opponent fills a missing one. */
    public static final class Versus implements MultiplayerModule {
        public static final Versus INSTANCE = new Versus();

        @Override
        public Identifier id() {
            return ident("multiplayer/test_versus");
        }

        @Override
        public List<PropertySpec> properties() {
            return List.of();
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of(ModuleFlag.MULTI_SEAT_ONLY, ModuleFlag.RUNTIME_OPPONENT);
        }

        @Override
        public int seats(PropertyValues props) {
            return 2;
        }

        @Override
        public SeatLayout layout(ModuleContext ctx) {
            return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
        }

        @Override
        public boolean sharesLives(PropertyValues props) {
            return false;
        }

        @Override
        public boolean runtimeOpponent(PropertyValues props) {
            return true;
        }

        @Override
        public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
            return fromRule;
        }
    }

    /** {@code arena/test_sized}: an open bordered arena of {@code side} cells (4..64) — for budget tests. */
    public static final class SizedArena implements ArenaModule {
        public static final SizedArena INSTANCE = new SizedArena();
        private static final List<PropertySpec> PROPS = List.of(PropertySpec.integer("side", 16, 4, 64));

        @Override
        public Identifier id() {
            return ident("arena/test_sized");
        }

        @Override
        public List<PropertySpec> properties() {
            return PROPS;
        }

        @Override
        public Set<ModuleFlag> flags() {
            return Set.of();
        }

        @Override
        public int width(PropertyValues props) {
            return props.getInt("side");
        }

        @Override
        public int height(PropertyValues props) {
            return props.getInt("side");
        }

        @Override
        public boolean wraps(PropertyValues props) {
            return false;
        }

        @Override
        public void build(ModuleContext ctx) {
            MatchState s = ctx.state();
            for (int x = 0; x < s.width(); x++) {
                s.setCell(x, 0, Cell.WALL);
                s.setCell(x, s.height() - 1, Cell.WALL);
            }
            for (int y = 0; y < s.height(); y++) {
                s.setCell(0, y, Cell.WALL);
                s.setCell(s.width() - 1, y, Cell.WALL);
            }
        }
    }

    /** Every test chip, for registration. */
    public static List<ArcadeModule> all() {
        return List.of(Bouncer.INSTANCE, Orb.INSTANCE, ScoreRule.INSTANCE, Mirror.INSTANCE, Coop.INSTANCE,
                Versus.INSTANCE, SizedArena.INSTANCE);
    }
}
