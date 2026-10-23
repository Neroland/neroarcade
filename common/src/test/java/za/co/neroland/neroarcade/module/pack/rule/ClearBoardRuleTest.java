package za.co.neroland.neroarcade.module.pack.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;

class ClearBoardRuleTest {

    /** A pickup chip that places two pickups far from the seat-0 spawn once and never respawns them. */
    private static PickupModule placedOnce(ModuleFlag... flags) {
        return new PickupModule() {
            @Override
            public Identifier id() {
                return TestModules.id("pickup/placed_once");
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
                ctx.state().spawn(EntityKind.PICKUP, (14 << 8) + 128, (14 << 8) + 128, -1, ctx.stackIndex());
                ctx.state().spawn(EntityKind.PICKUP, (14 << 8) + 128, (12 << 8) + 128, -1, ctx.stackIndex());
            }

            @Override
            public void tick(ModuleContext ctx) {
            }

            @Override
            public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
            }
        };
    }

    private static ResolvedGame withRuleProps(ResolvedGame g, PropertyValues p) {
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(), g.pawnProps(), g.rule(), p,
                g.hazards(), g.hazardProps(), g.pickups(), g.pickupProps(), g.control(), g.controlProps(), g.pace(),
                g.paceProps(), g.modifier(), g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(),
                g.themeProps(), g.unknownProperties());
    }

    private static PropertyValues seconds(int seconds) throws ValidationError {
        return PropertyValues.defaults(ClearBoardRule.INSTANCE).with("seconds", new PropertyValue.IntValue(seconds));
    }

    private static HeadlessMatch playing(ResolvedGame game, long seed) {
        return HeadlessMatch.of(game, 1, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static List<Integer> pickups(HeadlessMatch match) {
        List<Integer> ids = new ArrayList<>();
        match.state().forEach(EntityKind.PICKUP, ids::add);
        return ids;
    }

    @Test
    void winsWhenLastClearableGone() {
        HeadlessMatch match = playing(TestModules.resolvedOf(ClearBoardRule.INSTANCE,
                placedOnce(ModuleFlag.CLEARABLE)), 21L);
        assertEquals(120 * 20, match.state().timerTicks());
        assertEquals(3, match.state().lives(0));
        match.run(5, HeadlessMatch.InputScript.idle());
        assertTrue(match.result().isEmpty());
        List<Integer> ids = pickups(match);
        assertEquals(2, ids.size());
        match.state().remove(ids.get(0));
        match.step(SeatInput.NONE);
        assertTrue(match.result().isEmpty(), "one clearable left");
        match.state().remove(ids.get(1));
        match.step(SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask());
    }

    @Test
    void nonClearablePickupsDoNotCount() {
        HeadlessMatch match = playing(TestModules.resolvedOf(ClearBoardRule.INSTANCE,
                placedOnce(ModuleFlag.CLEARABLE), placedOnce(ModuleFlag.IS_KEY)), 22L);
        List<Integer> ids = new ArrayList<>();
        match.state().forEach(EntityKind.PICKUP, id -> {
            if (match.state().tag(id) == 0) {
                ids.add(id);
            }
        });
        ids.forEach(match.state()::remove);
        match.step(SeatInput.NONE);
        assertEquals(MatchResult.Outcome.WIN, match.result().orElseThrow().outcome(), "the key chip is not CLEARABLE");
    }

    @Test
    void timeOutLoses() throws ValidationError {
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(ClearBoardRule.INSTANCE,
                placedOnce(ModuleFlag.CLEARABLE)), seconds(10));
        HeadlessMatch match = HeadlessMatch.of(game, 1, 23L)
                .runToEnd(HeadlessMatch.InputScript.idle(), Budgets.COUNTDOWN_TICKS + 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.LOSE, result.outcome());
        assertEquals(0, result.winnersMask());
        assertEquals(200, result.ticks());
    }

    @Test
    void fallbackIsSurvive() throws ValidationError {
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(ClearBoardRule.INSTANCE), seconds(10));
        HeadlessMatch match = HeadlessMatch.of(game, 1, 24L)
                .runToEnd(HeadlessMatch.InputScript.idle(), Budgets.COUNTDOWN_TICKS + 400);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome(), "no CLEARABLE chip: survive the clock");
        assertEquals(1, result.winnersMask());
        assertEquals(200, result.ticks());
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(ClearBoardRule.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1002L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1002L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
