package za.co.neroland.neroarcade.module.pack.pace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.SeatInput;

class RampPaceTest {

    private static int divisorAt(PropertyValues props, int tick) {
        return RampPace.INSTANCE.divisor(new ModuleContext(null, props, null, null, tick, 0));
    }

    @Test
    void stepsDownEveryInterval() {
        PropertyValues defaults = PropertyValues.defaults(RampPace.INSTANCE);
        assertEquals(2, divisorAt(defaults, 0));
        assertEquals(2, divisorAt(defaults, 399));
        assertEquals(1, divisorAt(defaults, 400));
        assertEquals(1, divisorAt(defaults, 5000));

        // through the runtime: the divisor it applies follows the base tick, skipped ticks included
        ResolvedGame game = TestModules.resolvedOf(RampPace.INSTANCE);
        HeadlessMatch match = HeadlessMatch.of(game, 1, 51L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        match.step(SeatInput.NONE);
        assertEquals(2, match.state().paceDivisor());
        match.run(399, HeadlessMatch.InputScript.idle());
        assertEquals(400, match.state().tick());
        assertEquals(2, match.state().paceDivisor(), "tick 399 still slowed");
        match.step(SeatInput.NONE);
        assertEquals(1, match.state().paceDivisor(), "tick 400 is full speed");
    }

    @Test
    void floorIsOne() throws ValidationError {
        PropertyValues props = PropertyValues.defaults(RampPace.INSTANCE)
                .with("start_divisor", new PropertyValue.IntValue(4))
                .with("every_seconds", new PropertyValue.IntValue(5));
        assertEquals(4, divisorAt(props, 0));
        assertEquals(3, divisorAt(props, 100));
        assertEquals(2, divisorAt(props, 200));
        assertEquals(1, divisorAt(props, 300));
        assertEquals(1, divisorAt(props, 1_000_000));
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(RampPace.INSTANCE, TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1005L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1005L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
