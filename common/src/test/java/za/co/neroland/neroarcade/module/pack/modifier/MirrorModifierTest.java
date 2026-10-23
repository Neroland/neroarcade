package za.co.neroland.neroarcade.module.pack.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;

class MirrorModifierTest {

    private static Intent apply(String axis, Intent in) throws ValidationError {
        PropertyValues props = PropertyValues.defaults(MirrorModifier.INSTANCE)
                .with("axis", new PropertyValue.EnumValue(axis));
        return MirrorModifier.INSTANCE.transformIntent(new ModuleContext(null, props, null, null, 0, 0), 0, in);
    }

    @Test
    void flipsAxes() throws ValidationError {
        Intent in = new Intent(1, -1, 50, -20, true, false, true, false);
        assertEquals(new Intent(-1, -1, -50, -20, true, false, true, false), apply("horizontal", in));
        assertEquals(new Intent(1, 1, 50, 20, true, false, true, false), apply("vertical", in));
        assertEquals(new Intent(-1, 1, -50, 20, true, false, true, false), apply("both", in));
        assertEquals(Intent.NONE, apply("both", Intent.NONE));
        assertEquals("horizontal", PropertyValues.defaults(MirrorModifier.INSTANCE).getEnum("axis"));
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(MirrorModifier.INSTANCE, TestChips.Bouncer.INSTANCE,
                TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1008L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1008L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
