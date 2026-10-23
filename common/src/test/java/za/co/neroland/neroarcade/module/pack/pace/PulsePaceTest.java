package za.co.neroland.neroarcade.module.pack.pace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;

class PulsePaceTest {

    private static int divisorAt(int tick) {
        return PulsePace.INSTANCE.divisor(
                new ModuleContext(null, PropertyValues.defaults(PulsePace.INSTANCE), null, null, tick, 0));
    }

    @Test
    void alternates() {
        for (int t = 0; t < 60; t++) {
            assertEquals(1, divisorAt(t), "tick " + t);
        }
        for (int t = 60; t < 120; t++) {
            assertEquals(2, divisorAt(t), "tick " + t);
        }
        assertEquals(1, divisorAt(120));
        assertEquals(2, divisorAt(180));
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(PulsePace.INSTANCE, TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1006L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1006L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
