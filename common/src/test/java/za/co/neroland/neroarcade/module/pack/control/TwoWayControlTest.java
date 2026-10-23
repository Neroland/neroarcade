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

class TwoWayControlTest {

    private static final TwoWayControl CHIP = TwoWayControl.INSTANCE;

    private static PropertyValues axis(String axis) throws Exception {
        return PropertyValues.of(CHIP, Map.of("axis", new PropertyValue.EnumValue(axis)));
    }

    private static Intent interpret(PropertyValues props, SeatInput now, SeatInput previous) {
        return CHIP.interpret(new ModuleContext(null, props, null, null, 0, 0), 0, now, previous);
    }

    @Test
    void declaresOneAxisOnly() {
        assertEquals(Set.of(ModuleFlag.ONE_AXIS), CHIP.flags());
        assertEquals("neroarcade:control/two_way", CHIP.id().toString());
        assertEquals(TwoWayControl.HORIZONTAL, PropertyValues.defaults(CHIP).getEnum("axis"));
    }

    @Test
    void dropsOtherAxis() throws Exception {
        SeatInput upRight = SeatInput.of(SeatInput.UP | SeatInput.RIGHT, 90, -90);
        Intent h = interpret(PropertyValues.defaults(CHIP), upRight, SeatInput.NONE);
        assertEquals(1, h.moveX());
        assertEquals(0, h.moveY());
        assertEquals(0, h.aimX());
        assertEquals(0, h.aimY());
        Intent left = interpret(PropertyValues.defaults(CHIP), SeatInput.of(SeatInput.LEFT | SeatInput.DOWN, 0, 0),
                SeatInput.NONE);
        assertEquals(-1, left.moveX());
        assertEquals(0, left.moveY());
        Intent v = interpret(axis(TwoWayControl.VERTICAL), upRight, SeatInput.NONE);
        assertEquals(0, v.moveX());
        assertEquals(-1, v.moveY());
        Intent down = interpret(axis(TwoWayControl.VERTICAL), SeatInput.of(SeatInput.DOWN | SeatInput.LEFT, 0, 0),
                SeatInput.NONE);
        assertEquals(0, down.moveX());
        assertEquals(1, down.moveY());
        Intent both = interpret(PropertyValues.defaults(CHIP), SeatInput.of(SeatInput.LEFT | SeatInput.RIGHT, 0, 0),
                SeatInput.NONE);
        assertEquals(0, both.moveX(), "opposite directions cancel");
    }

    @Test
    void edgesDetected() {
        PropertyValues props = PropertyValues.defaults(CHIP);
        SeatInput a = SeatInput.of(SeatInput.A, 0, 0);
        SeatInput ab = SeatInput.of(SeatInput.A | SeatInput.B, 0, 0);
        Intent first = interpret(props, a, SeatInput.NONE);
        assertTrue(first.a());
        assertTrue(first.aPressed());
        assertFalse(first.b());
        assertFalse(first.bPressed());
        Intent held = interpret(props, ab, a);
        assertTrue(held.a());
        assertFalse(held.aPressed(), "A held from the previous tick is not a new press");
        assertTrue(held.b());
        assertTrue(held.bPressed());
        Intent released = interpret(props, SeatInput.NONE, ab);
        assertFalse(released.a());
        assertFalse(released.b());
        assertFalse(released.aPressed());
        assertFalse(released.bPressed());
    }

    @Test
    void legendNamesTheAxis() throws Exception {
        assertEquals(List.of("neroarcade.legend.move_2way_h"), CHIP.legend(PropertyValues.defaults(CHIP)));
        assertEquals(List.of("neroarcade.legend.move_2way_v"), CHIP.legend(axis(TwoWayControl.VERTICAL)));
        assertEquals(1, CHIP.effectArgs(PropertyValues.defaults(CHIP)).length);
    }

    @Test
    void replayStable() {
        long first = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x2A11L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long second = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x2A11L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(first, second);
    }
}
