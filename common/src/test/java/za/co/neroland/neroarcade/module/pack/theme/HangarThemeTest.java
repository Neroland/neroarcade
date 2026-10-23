package za.co.neroland.neroarcade.module.pack.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ThemeSpec;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;

class HangarThemeTest {

    private static final HangarTheme CHIP = HangarTheme.INSTANCE;

    @Test
    void specIdsFollowConvention() {
        ThemeSpec spec = CHIP.spec(PropertyValues.defaults(CHIP));
        assertEquals("neroarcade:theme/hangar", CHIP.id().toString());
        assertEquals("neroarcade:palette/hangar_0", spec.paletteId().toString());
        assertEquals("neroarcade:sprites/hangar", spec.spriteSetId().toString());
        assertEquals("neroarcade:theme.hangar.start", spec.soundStart().toString());
        assertEquals("neroarcade:game.pickup", spec.soundPickup().toString());
        assertEquals("neroarcade:theme.hangar.end", spec.soundEnd().toString());
        assertTrue(CHIP.properties().isEmpty(), "no property without a visible effect (Stage 2 F10)");
        assertTrue(CHIP.flags().isEmpty());
    }

    @Test
    void replayStable() {
        long first = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x4E02L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long second = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x4E02L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(first, second);
    }
}
