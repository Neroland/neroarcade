package za.co.neroland.neroarcade.module.pack.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

class ReachExitRuleTest {

    /** Two IS_KEY pickups, placed once at match start and never respawned. */
    private static PickupModule keys() {
        return new PickupModule() {
            @Override
            public Identifier id() {
                return TestModules.id("pickup/test_keys");
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(ModuleFlag.IS_KEY);
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
                ctx.state().spawn(EntityKind.PICKUP, (8 << 8) + 128, (8 << 8) + 128, -1, ctx.stackIndex());
                ctx.state().spawn(EntityKind.PICKUP, (10 << 8) + 128, (6 << 8) + 128, -1, ctx.stackIndex());
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

    private static PropertyValues needsKey() throws ValidationError {
        return PropertyValues.defaults(ReachExitRule.INSTANCE).with("needs_key", new PropertyValue.BoolValue(true));
    }

    /** Every cell holding {@code value}, as {x, y} pairs in row-major order. */
    private static List<int[]> cells(MatchState state, byte value) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                if (state.cell(x, y) == value) {
                    out.add(new int[] {x, y});
                }
            }
        }
        return out;
    }

    private static int[] pawnCell(MatchState state, int seat) {
        int id = state.pawnId(seat);
        return new int[] {state.get(id, Entities.X) >> MatchState.FP_SHIFT, state.get(id, Entities.Y) >> MatchState.FP_SHIFT};
    }

    @Test
    void placesExitFarCorner() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(ReachExitRule.INSTANCE), 1, 31L);
        MatchState state = match.state();
        List<int[]> exits = cells(state, Cell.EXIT);
        assertEquals(1, exits.size());
        int[] spawn = pawnCell(state, 0);
        assertEquals(15 - spawn[0], exits.get(0)[0]);
        assertEquals(15 - spawn[1], exits.get(0)[1]);
        assertEquals(0, state.timerTicks(), "no clock");
        assertEquals(3, state.lives(0));
        assertTrue(cells(state, Cell.EXIT_LOCKED).isEmpty());
    }

    @Test
    void oneAxisPawnGetsExitOnItsLine() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(ReachExitRule.INSTANCE,
                TestModules.pawn(ModuleFlag.ONE_AXIS)), 1, 32L);
        MatchState state = match.state();
        List<int[]> exits = cells(state, Cell.EXIT);
        assertEquals(1, exits.size());
        int[] spawn = pawnCell(state, 0);
        assertEquals(spawn[1], exits.get(0)[1], "exit y == spawn y");
        assertEquals(spawn[0] < 8 ? 14 : 1, exits.get(0)[0], "far end of the line");
    }

    @Test
    void lockedUntilKeysGone() throws ValidationError {
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(ReachExitRule.INSTANCE, keys()), needsKey());
        HeadlessMatch match = HeadlessMatch.of(game, 1, 33L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        MatchState state = match.state();
        assertTrue(cells(state, Cell.EXIT).isEmpty());
        assertEquals(1, cells(state, Cell.EXIT_LOCKED).size());
        match.run(10, HeadlessMatch.InputScript.idle());
        assertEquals(1, cells(state, Cell.EXIT_LOCKED).size(), "keys still on the board");
        List<Integer> ids = new ArrayList<>();
        state.forEach(EntityKind.PICKUP, ids::add);
        assertEquals(2, ids.size());
        state.remove(ids.get(0));
        match.step(SeatInput.NONE);
        assertEquals(1, cells(state, Cell.EXIT_LOCKED).size(), "one key left");
        state.remove(ids.get(1));
        match.step(SeatInput.NONE);
        assertTrue(cells(state, Cell.EXIT_LOCKED).isEmpty());
        assertEquals(1, cells(state, Cell.EXIT).size());
        assertTrue(match.result().isEmpty());
    }

    @Test
    void needsKeyWithoutKeyChipOpensAtOnce() throws ValidationError {
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(ReachExitRule.INSTANCE), needsKey());
        HeadlessMatch match = HeadlessMatch.of(game, 1, 34L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        assertEquals(1, cells(match.state(), Cell.EXIT_LOCKED).size());
        match.step(SeatInput.NONE);
        assertTrue(cells(match.state(), Cell.EXIT_LOCKED).isEmpty());
        assertEquals(1, cells(match.state(), Cell.EXIT).size());
    }

    @Test
    void pawnOnExitWins() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(ReachExitRule.INSTANCE), 1, 35L)
                .run(Budgets.COUNTDOWN_TICKS + 3, HeadlessMatch.InputScript.idle());
        MatchState state = match.state();
        assertTrue(match.result().isEmpty());
        int[] exit = cells(state, Cell.EXIT).get(0);
        int pawn = state.pawnId(0);
        assertNotNull(exit);
        state.set(pawn, Entities.X, (exit[0] << MatchState.FP_SHIFT) + MatchState.FP / 2);
        state.set(pawn, Entities.Y, (exit[1] << MatchState.FP_SHIFT) + MatchState.FP / 2);
        match.step(SeatInput.NONE);
        MatchResult result = match.result().orElseThrow();
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(1, result.winnersMask());
    }

    @Test
    void replayStable() throws ValidationError {
        ResolvedGame game = withRuleProps(TestModules.resolvedOf(ReachExitRule.INSTANCE, TestChips.Bouncer.INSTANCE,
                keys()), needsKey());
        long a = HeadlessMatch.of(game, 1, 1003L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1003L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
