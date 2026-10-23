package za.co.neroland.neroarcade.module.pack.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

class WrapAroundModifierTest {

    /** A 16×8 board with a wall border that already wraps (the arena's own topology). */
    private static final ArenaModule TUBE = new ArenaModule() {
        @Override
        public Identifier id() {
            return TestModules.id("arena/test_tube");
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
        public int width(PropertyValues props) {
            return 16;
        }

        @Override
        public int height(PropertyValues props) {
            return 8;
        }

        @Override
        public boolean wraps(PropertyValues props) {
            return true;
        }

        @Override
        public void build(ModuleContext ctx) {
            MatchState s = ctx.state();
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) {
                    boolean border = x == 0 || y == 0 || x == s.width() - 1 || y == s.height() - 1;
                    s.setCell(x, y, border ? Cell.WALL : Cell.EMPTY);
                }
            }
        }
    };

    private static ResolvedGame withModifierProps(ResolvedGame g, PropertyValues p) {
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(), g.pawnProps(), g.rule(), g.ruleProps(),
                g.hazards(), g.hazardProps(), g.pickups(), g.pickupProps(), g.control(), g.controlProps(), g.pace(),
                g.paceProps(), g.modifier(), java.util.Optional.of(p), g.multiplayer(), g.multiplayerProps(),
                g.theme(), g.themeProps(), g.unknownProperties());
    }

    private static byte[] cells(MatchState s) {
        byte[] out = new byte[s.width() * s.height()];
        for (int i = 0; i < out.length; i++) {
            out[i] = s.cellAt(i);
        }
        return out;
    }

    @Test
    void opensBorderAndWraps() {
        HeadlessMatch match = HeadlessMatch.of(TestModules.resolvedOf(WrapAroundModifier.INSTANCE), 1, 61L);
        MatchState state = match.state();
        assertTrue(state.wrapsX());
        assertTrue(state.wrapsY());
        assertEquals(Cell.EMPTY, state.cell(0, 5));
        assertEquals(Cell.EMPTY, state.cell(15, 5));
        assertEquals(Cell.EMPTY, state.cell(5, 0));
        assertEquals(Cell.EMPTY, state.cell(5, 15));
        assertEquals(Cell.EMPTY, state.cell(0, 0), "corners open too");

        match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        int pawn = state.pawnId(0);
        assertEquals(1, state.get(pawn, Entities.X) >> MatchState.FP_SHIFT, "spawned at x = 1");
        boolean arrived = false;
        for (int t = 0; t < 12 && !arrived; t++) {
            match.step(SeatInput.of(SeatInput.LEFT, 0, 0));
            arrived = (state.get(state.pawnId(0), Entities.X) >> MatchState.FP_SHIFT) == 15;
        }
        assertTrue(arrived, "leaving at x = 0 arrives at x = 15");
    }

    @Test
    void horizontalOnlyOpensSideColumns() throws ValidationError {
        ResolvedGame game = withModifierProps(TestModules.resolvedOf(WrapAroundModifier.INSTANCE),
                PropertyValues.defaults(WrapAroundModifier.INSTANCE).with("axes", new PropertyValue.EnumValue("horizontal")));
        MatchState state = HeadlessMatch.of(game, 1, 62L).state();
        assertTrue(state.wrapsX());
        assertFalse(state.wrapsY());
        assertEquals(Cell.EMPTY, state.cell(0, 5));
        assertEquals(Cell.WALL, state.cell(5, 0));
        assertEquals(Cell.WALL, state.cell(5, 15));
    }

    @Test
    void ringAlreadyWrapsUnchanged() {
        MatchState plain = HeadlessMatch.of(TestModules.resolvedOf(TUBE), 1, 63L).state();
        MatchState wrapped = HeadlessMatch.of(TestModules.resolvedOf(TUBE, WrapAroundModifier.INSTANCE), 1, 63L).state();
        assertTrue(wrapped.wrapsX());
        assertTrue(wrapped.wrapsY());
        assertEquals(Cell.WALL, wrapped.cell(0, 3));
        assertTrue(java.util.Arrays.equals(cells(plain), cells(wrapped)), "an already-wrapping axis keeps its walls");
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(WrapAroundModifier.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1007L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1007L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
