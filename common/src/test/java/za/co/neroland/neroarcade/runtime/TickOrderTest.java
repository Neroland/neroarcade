package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;

/** The contract's invocation order for one simulated tick, with stacks of three and four seats. */
class TickOrderTest {

    private final List<String> log = new ArrayList<>();
    private int divisor = 1;

    private abstract static class Stub {
        public List<PropertySpec> properties() {
            return List.of();
        }

        public Set<ModuleFlag> flags() {
            return Set.of();
        }
    }

    private Identifier id(String path) {
        return TestModules.id(path);
    }

    private ResolvedGame game() {
        PaceModule pace = new PaceModuleStub();
        ControlModule control = new ControlStub();
        MultiplayerModule mp = new MultiplayerStub();
        ModifierModule modifier = new ModifierStub();
        PawnModule pawn = new PawnStub();
        RuleModule rule = new RuleStub();
        return TestModules.resolvedOf(new ArenaStub(), pace, control, mp, modifier, pawn, rule, new HazardStub("hazard/h0"),
                new HazardStub("hazard/h1"), new HazardStub("hazard/h2"), new PickupStub("pickup/p0"),
                new PickupStub("pickup/p1"), new PickupStub("pickup/p2"));
    }

    @Test
    void oneSimulatedTickCallsHooksInContractOrder() {
        HeadlessMatch match = HeadlessMatch.of(game(), 4, 3L);
        match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        log.clear();
        match.step();
        List<String> expected = new ArrayList<>();
        expected.add("pace.divisor");
        expected.add("arena.tick");
        for (int s = 0; s < 4; s++) {
            expected.add("control.interpret:" + s);
        }
        expected.add("mp.active");
        for (int s = 0; s < 4; s++) {
            expected.add("modifier.transform:" + s);
        }
        for (int s = 0; s < 4; s++) {
            expected.add("pawn.apply:" + s);
        }
        for (int s = 0; s < 4; s++) {
            expected.add("pawn.tick:" + s);
        }
        for (int i = 0; i < 3; i++) {
            expected.add("hazard.tick:" + i);
        }
        for (int i = 0; i < 3; i++) {
            expected.add("pickup.tick:" + i);
        }
        expected.add("modifier.after");
        expected.add("mp.after");
        expected.add("rule.evaluate");
        expected.add("mp.resolve");
        assertEquals(expected, log);
    }

    @Test
    void paceDivisorTwoSkipsOddTicks() {
        divisor = 2;
        HeadlessMatch match = HeadlessMatch.of(game(), 4, 3L);
        match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        match.step();
        assertEquals(1, match.state().tick());
        log.clear();
        match.step();
        assertEquals(List.of("pace.divisor"), log, "odd tick: only the pace is asked, no arena.tick");
        assertEquals(2, match.state().tick());
        assertEquals(2, match.state().paceDivisor());
        log.clear();
        match.step();
        assertEquals(List.of("pace.divisor", "arena.tick"), log.subList(0, 2), "even tick: the arena ticks second");
        assertEquals("rule.evaluate", log.get(log.size() - 2));
    }

    @Test
    void matchStartOrder() {
        HeadlessMatch.of(game(), 2, 3L);
        assertEquals(List.of("rule.start", "hazard.start:0", "hazard.start:1", "hazard.start:2", "pickup.start:0",
                "pickup.start:1", "pickup.start:2", "modifier.start", "mp.layout", "pawn.spawn:0", "pawn.spawn:1"), log);
    }

    /** grid_16's board with a logged {@code tick}. */
    private final class ArenaStub extends Stub implements ArenaModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("arena/rec");
        }

        @Override
        public List<PropertySpec> properties() {
            return Grid16Arena.INSTANCE.properties();
        }

        @Override
        public int width(PropertyValues props) {
            return Grid16Arena.INSTANCE.width(props);
        }

        @Override
        public int height(PropertyValues props) {
            return Grid16Arena.INSTANCE.height(props);
        }

        @Override
        public boolean wraps(PropertyValues props) {
            return false;
        }

        @Override
        public void build(ModuleContext ctx) {
            Grid16Arena.INSTANCE.build(ctx);
        }

        @Override
        public void tick(ModuleContext ctx) {
            log.add("arena.tick");
        }
    }

    private final class PaceModuleStub extends Stub implements PaceModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("pace/rec");
        }

        @Override
        public int divisor(ModuleContext ctx) {
            log.add("pace.divisor");
            return divisor;
        }
    }

    private final class ControlStub extends Stub implements ControlModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("control/rec");
        }

        @Override
        public Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous) {
            log.add("control.interpret:" + seat);
            return Intent.NONE;
        }

        @Override
        public List<String> legend(PropertyValues props) {
            return List.of();
        }
    }

    private final class MultiplayerStub extends Stub implements MultiplayerModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("multiplayer/rec");
        }

        @Override
        public int seats(PropertyValues props) {
            return 4;
        }

        @Override
        public SeatLayout layout(ModuleContext ctx) {
            log.add("mp.layout");
            return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
        }

        @Override
        public boolean sharesLives(PropertyValues props) {
            return false;
        }

        @Override
        public int activeSeatMask(ModuleContext ctx) {
            log.add("mp.active");
            return ctx.state().aliveSeatMask();
        }

        @Override
        public void afterTick(ModuleContext ctx) {
            log.add("mp.after");
        }

        @Override
        public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
            log.add("mp.resolve");
            return fromRule;
        }
    }

    private final class ModifierStub extends Stub implements ModifierModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("modifier/rec");
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            log.add("modifier.start");
        }

        @Override
        public Intent transformIntent(ModuleContext ctx, int seat, Intent in) {
            log.add("modifier.transform:" + seat);
            return in;
        }

        @Override
        public void afterTick(ModuleContext ctx) {
            log.add("modifier.after");
        }
    }

    private final class PawnStub extends Stub implements PawnModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("pawn/rec");
        }

        @Override
        public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
            log.add("pawn.spawn:" + seat);
            ctx.state().setPawnId(seat, ctx.state().spawn(EntityKind.PAWN, (cellX << 8) + 128, (cellY << 8) + 128, seat, 0));
        }

        @Override
        public void applyInput(ModuleContext ctx, int seat, Intent intent) {
            log.add("pawn.apply:" + seat);
        }

        @Override
        public void tick(ModuleContext ctx, int seat) {
            log.add("pawn.tick:" + seat);
        }
    }

    private final class RuleStub extends Stub implements RuleModule {
        @Override
        public Identifier id() {
            return TickOrderTest.this.id("rule/rec");
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            log.add("rule.start");
        }

        @Override
        public RuleOutcome evaluate(ModuleContext ctx) {
            log.add("rule.evaluate");
            return RuleOutcome.CONTINUE;
        }
    }

    private final class HazardStub extends Stub implements HazardModule {
        private final String path;

        HazardStub(String path) {
            this.path = path;
        }

        @Override
        public Identifier id() {
            return TickOrderTest.this.id(path);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            log.add("hazard.start:" + ctx.stackIndex());
        }

        @Override
        public void tick(ModuleContext ctx) {
            log.add("hazard.tick:" + ctx.stackIndex());
        }
    }

    private final class PickupStub extends Stub implements PickupModule {
        private final String path;

        PickupStub(String path) {
            this.path = path;
        }

        @Override
        public Identifier id() {
            return TickOrderTest.this.id(path);
        }

        @Override
        public void onMatchStart(ModuleContext ctx) {
            log.add("pickup.start:" + ctx.stackIndex());
        }

        @Override
        public void tick(ModuleContext ctx) {
            log.add("pickup.tick:" + ctx.stackIndex());
        }

        @Override
        public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
            log.add("pickup.collect:" + ctx.stackIndex());
        }
    }
}
