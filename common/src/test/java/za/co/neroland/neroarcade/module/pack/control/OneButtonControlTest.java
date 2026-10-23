package za.co.neroland.neroarcade.module.pack.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.SeatInput;

class OneButtonControlTest {

    private static final OneButtonControl CHIP = OneButtonControl.INSTANCE;

    private static PropertyValues hold(boolean hold) throws Exception {
        return PropertyValues.of(CHIP, Map.of("hold", new PropertyValue.BoolValue(hold)));
    }

    private static Intent interpret(PropertyValues props, SeatInput now, SeatInput previous) {
        return CHIP.interpret(new ModuleContext(null, props, null, null, 0, 0), 0, now, previous);
    }

    @Test
    void declaresButtonsOnly() {
        assertEquals(Set.of(ModuleFlag.ONE_AXIS, ModuleFlag.BUTTONS_ONLY), CHIP.flags());
        assertFalse(PropertyValues.defaults(CHIP).getBool("hold"));
    }

    @Test
    void noMovement() throws Exception {
        int all = SeatInput.UP | SeatInput.DOWN | SeatInput.LEFT | SeatInput.RIGHT;
        for (int buttons : new int[] {SeatInput.UP, SeatInput.RIGHT, SeatInput.DOWN | SeatInput.LEFT, all,
                all | SeatInput.A}) {
            for (PropertyValues props : List.of(hold(false), hold(true))) {
                Intent intent = interpret(props, SeatInput.of(buttons, 127, -127), SeatInput.NONE);
                assertEquals(0, intent.moveX());
                assertEquals(0, intent.moveY());
                assertEquals(0, intent.aimX());
                assertEquals(0, intent.aimY());
            }
        }
    }

    @Test
    void holdFalseLevelEqualsEdge() throws Exception {
        PropertyValues props = hold(false);
        SeatInput a = SeatInput.of(SeatInput.A, 0, 0);
        SeatInput b = SeatInput.of(SeatInput.B, 0, 0);
        Intent press = interpret(props, a, SeatInput.NONE);
        assertTrue(press.a());
        assertTrue(press.aPressed());
        Intent stillHeld = interpret(props, a, a);
        assertFalse(stillHeld.a(), "holding A does nothing more than tapping it");
        assertFalse(stillHeld.aPressed());
        Intent pressB = interpret(props, b, SeatInput.NONE);
        assertTrue(pressB.b());
        assertTrue(pressB.bPressed());
        assertFalse(pressB.a());
        Intent heldB = interpret(props, b, b);
        assertFalse(heldB.b());
        assertFalse(heldB.bPressed());
        assertEquals(press.a(), press.aPressed());
        assertEquals(stillHeld.a(), stillHeld.aPressed());
    }

    @Test
    void holdTrueLevelIsHeld() throws Exception {
        PropertyValues props = hold(true);
        SeatInput ab = SeatInput.of(SeatInput.A | SeatInput.B, 0, 0);
        Intent press = interpret(props, ab, SeatInput.NONE);
        assertTrue(press.a());
        assertTrue(press.b());
        assertTrue(press.aPressed());
        assertTrue(press.bPressed());
        Intent held = interpret(props, ab, ab);
        assertTrue(held.a());
        assertTrue(held.b());
        assertFalse(held.aPressed());
        assertFalse(held.bPressed());
        Intent released = interpret(props, SeatInput.NONE, ab);
        assertFalse(released.a());
        assertFalse(released.b());
    }

    @Test
    void legendHasOneLine() throws Exception {
        assertEquals(List.of("neroarcade.legend.one_button"), CHIP.legend(hold(false)));
        assertEquals(List.of("neroarcade.legend.one_button"), CHIP.legend(hold(true)));
    }

    @Test
    void replayStable() {
        long first = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x1B07L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long second = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x1B07L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(first, second);
    }
}
