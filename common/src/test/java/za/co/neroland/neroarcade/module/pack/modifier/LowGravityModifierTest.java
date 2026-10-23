package za.co.neroland.neroarcade.module.pack.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class LowGravityModifierTest {

    @Test
    void gravityIsScaled() throws ValidationError {
        assertEquals(MatchState.DEFAULT_GRAVITY, HeadlessMatch.of(TestModules.resolvedOf(), 1, 81L).state().gravity());
        MatchState state = HeadlessMatch.of(TestModules.resolvedOf(LowGravityModifier.INSTANCE), 1, 81L).state();
        assertEquals(16, state.gravity());

        PropertyValues tenth = PropertyValues.defaults(LowGravityModifier.INSTANCE)
                .with("percent", new PropertyValue.IntValue(10));
        LowGravityModifier.INSTANCE.onMatchStart(new ModuleContext(state, tenth, null, null, 0, 0));
        assertEquals(3, state.gravity(), "32 * 10 / 100, integer");
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(LowGravityModifier.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1010L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1010L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
