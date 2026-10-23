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

class TwinStickControlTest {

    private static final TwinStickControl CHIP = TwinStickControl.INSTANCE;

    private static PropertyValues aim(String aim) throws Exception {
        return PropertyValues.of(CHIP, Map.of("aim", new PropertyValue.EnumValue(aim)));
    }

    private static Intent interpret(PropertyValues props, SeatInput now, SeatInput previous) {
        return CHIP.interpret(new ModuleContext(null, props, null, null, 0, 0), 0, now, previous);
    }

    private static Intent aimAt(PropertyValues props, int x, int y) {
        return interpret(props, SeatInput.of(0, x, y), SeatInput.NONE);
    }

    @Test
    void declaresNeedsAim() {
        assertEquals(Set.of(ModuleFlag.NEEDS_AIM), CHIP.flags());
        assertEquals(TwinStickControl.EIGHT_WAY, PropertyValues.defaults(CHIP).getEnum("aim"));
    }

    @Test
    void movesInEightDirectionsWithButtonsAsFourWay() {
        SeatInput upLeftA = SeatInput.of(SeatInput.UP | SeatInput.LEFT | SeatInput.A, 0, 0);
        Intent intent = interpret(PropertyValues.defaults(CHIP), upLeftA, SeatInput.NONE);
        assertEquals(-1, intent.moveX());
        assertEquals(-1, intent.moveY(), "diagonals are always on");
        assertTrue(intent.a());
        assertTrue(intent.aPressed());
        assertFalse(intent.b());
        Intent held = interpret(PropertyValues.defaults(CHIP), upLeftA, upLeftA);
        assertTrue(held.a());
        assertFalse(held.aPressed());
    }

    @Test
    void quantisesEightWay() {
        PropertyValues props = PropertyValues.defaults(CHIP);
        Intent a = aimAt(props, 100, -40);
        assertEquals(127, a.aimX());
        assertEquals(-127, a.aimY());
        Intent b = aimAt(props, -127, 64);
        assertEquals(-127, b.aimX());
        assertEquals(127, b.aimY());
        Intent c = aimAt(props, 5, 127);
        assertEquals(0, c.aimX());
        assertEquals(127, c.aimY());
        assertEquals(0, a.moveX(), "aim never moves the pawn");
        assertEquals(0, a.moveY());
    }

    @Test
    void freePassesThrough() throws Exception {
        PropertyValues props = aim(TwinStickControl.FREE);
        Intent a = aimAt(props, 100, -40);
        assertEquals(100, a.aimX());
        assertEquals(-40, a.aimY());
        Intent b = aimAt(props, 5, -31);
        assertEquals(5, b.aimX(), "no dead zone when free");
        assertEquals(-31, b.aimY());
        Intent c = aimAt(props, 127, -127);
        assertEquals(127, c.aimX());
        assertEquals(-127, c.aimY());
        Intent clamped = interpret(props, new SeatInput(0x0080_8000), SeatInput.NONE); // raw -128 on both axes
        assertEquals(-127, clamped.aimX(), "clamped to ±127");
        assertEquals(-127, clamped.aimY());
        assertEquals(127, TwinStickControl.clamp(400));
        assertEquals(-127, TwinStickControl.clamp(-400));
    }

    @Test
    void deadZone() {
        PropertyValues props = PropertyValues.defaults(CHIP);
        Intent inside = aimAt(props, 31, -31);
        assertEquals(0, inside.aimX());
        assertEquals(0, inside.aimY());
        Intent edge = aimAt(props, 32, -32);
        assertEquals(127, edge.aimX());
        assertEquals(-127, edge.aimY());
        Intent zero = aimAt(props, 0, 0);
        assertEquals(0, zero.aimX());
        assertEquals(0, zero.aimY());
    }

    @Test
    void legendIsMoveThenAim() {
        assertEquals(List.of("neroarcade.legend.move_8way", "neroarcade.legend.aim_stick"),
                CHIP.legend(PropertyValues.defaults(CHIP)));
    }

    @Test
    void replayStable() {
        HeadlessMatch.InputScript aiming = (tick, seat, rng) -> {
            int buttons = switch (rng.nextInt(6)) {
                case 1 -> SeatInput.UP | SeatInput.A;
                case 2 -> SeatInput.DOWN;
                case 3 -> SeatInput.LEFT | SeatInput.UP;
                case 4 -> SeatInput.RIGHT | SeatInput.B;
                case 5 -> SeatInput.A;
                default -> 0;
            };
            return SeatInput.of(buttons, rng.nextInt(-127, 127), rng.nextInt(-127, 127));
        };
        long first = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x7517L).run(600, aiming).hash();
        long second = HeadlessMatch.of(TestModules.resolvedOf(CHIP), 1, 0x7517L).run(600, aiming).hash();
        assertEquals(first, second);
    }
}
