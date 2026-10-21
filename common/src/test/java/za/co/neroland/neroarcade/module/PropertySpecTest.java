package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;

class PropertySpecTest {

    @Test
    void namesAndConstantsFollowTheRegex() {
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("Speed", 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("1speed", 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("a_very_long_name_x", 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.enumeration("axis", "Up", "Up", "down"));
        PropertySpec.integer("a_valid_name_123", 1, 0, 2);
    }

    @Test
    void intDefaultInsideRange() {
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("speed", 6, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("speed", 0, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.integer("speed", 3, 5, 1));
    }

    @Test
    void enumDefaultIsAConstantAndCountBounded() {
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.enumeration("axis", "diag", "horizontal", "vertical"));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.enumeration("axis", "one", "one"));
        assertThrows(IllegalArgumentException.class,
                () -> PropertySpec.enumeration("axis", "a", "a", "b", "c", "d", "e", "f", "g", "h", "i"));
        assertThrows(IllegalArgumentException.class, () -> PropertySpec.enumeration("axis", "a", "a", "a"));
    }

    @Test
    void coerceAcceptsNbtBoolShapes() throws ValidationError {
        PropertySpec bool = PropertySpec.bool("fires", true);
        assertEquals(new BoolValue(true), bool.coerce(new IntValue(1)));
        assertEquals(new BoolValue(false), bool.coerce(new IntValue(0)));
        assertEquals(new BoolValue(false), bool.coerce(new BoolValue(false)));
        assertThrows(ValidationError.class, () -> bool.coerce(new IntValue(2)));
        PropertySpec unit = PropertySpec.integer("flag", 0, 0, 1);
        assertEquals(new IntValue(1), unit.coerce(new BoolValue(true)));
        assertThrows(ValidationError.class, () -> PropertySpec.integer("speed", 3, 1, 5).coerce(new BoolValue(true)));
    }

    @Test
    void outOfRangeAndUnknownConstantAreBadProperty() {
        ValidationError range = assertThrows(ValidationError.class,
                () -> PropertySpec.integer("speed", 3, 1, 5).coerce(new IntValue(6)));
        assertEquals(ValidationError.Code.BAD_PROPERTY, range.code);
        ValidationError constant = assertThrows(ValidationError.class,
                () -> PropertySpec.enumeration("axis", "horizontal", "horizontal", "vertical").coerce(new EnumValue("diagonal")));
        assertEquals(ValidationError.Code.BAD_PROPERTY, constant.code);
        assertThrows(ValidationError.class,
                () -> PropertySpec.enumeration("axis", "horizontal", "horizontal", "vertical").coerce(new IntValue(0)));
    }
}
