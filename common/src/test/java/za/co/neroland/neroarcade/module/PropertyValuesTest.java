package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;

class PropertyValuesTest {

    enum Obstacles { NONE, PILLARS, MAZE_LITE }

    @Test
    void defaultsFillEveryProperty() {
        PropertyValues values = PropertyValues.defaults(SurviveTimeRule.INSTANCE);
        assertEquals(60, values.getInt("seconds"));
        assertEquals(1, values.getInt("lives"));
        assertTrue(values.explicit().isEmpty());
    }

    @Test
    void explicitOmitsDefaultsAndValuesEqualToDefaults() throws ValidationError {
        PropertyValues values = PropertyValues.of(SurviveTimeRule.INSTANCE,
                Map.of("seconds", new IntValue(60), "lives", new IntValue(3)));
        assertEquals(Map.of("lives", new IntValue(3)), values.explicit());
    }

    @Test
    void ofCoercesValidatesAndIgnoresUnknownNames() throws ValidationError {
        PropertyValues values = PropertyValues.of(FourWayControl.INSTANCE,
                Map.of("diagonals", new IntValue(1), "nonsense", new IntValue(5)));
        assertTrue(values.getBool("diagonals"));
        assertTrue(PropertyValues.hasUnknown(FourWayControl.INSTANCE, Map.of("nonsense", new IntValue(5))));
        ValidationError bad = assertThrows(ValidationError.class,
                () -> PropertyValues.of(SurviveTimeRule.INSTANCE, Map.of("lives", new IntValue(10))));
        assertEquals(ValidationError.Code.BAD_PROPERTY, bad.code);
        assertEquals("lives", bad.args.get(0));
        assertEquals("neroarcade:rule/survive_time", bad.args.get(1));
    }

    @Test
    void withValidates() throws ValidationError {
        PropertyValues values = PropertyValues.defaults(SurviveTimeRule.INSTANCE);
        assertEquals(5, values.with("lives", new IntValue(5)).getInt("lives"));
        assertEquals(1, values.getInt("lives"));
        assertThrows(ValidationError.class, () -> values.with("lives", new IntValue(0)));
        assertThrows(IllegalArgumentException.class, () -> values.with("nonsense", new IntValue(0)));
    }

    @Test
    void wrongKindGetterIsAProgrammingError() {
        PropertyValues values = PropertyValues.defaults(FourWayControl.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> values.getInt("diagonals"));
        assertThrows(IllegalArgumentException.class, () -> values.getBool("missing"));
    }

    @Test
    void enumGettersAndEqualityByContent() throws ValidationError {
        PropertyValues a = PropertyValues.of(Grid16Arena.INSTANCE, Map.of("obstacles", new EnumValue("maze_lite")));
        PropertyValues b = PropertyValues.defaults(Grid16Arena.INSTANCE).with("obstacles", new EnumValue("maze_lite"));
        assertEquals(Obstacles.MAZE_LITE, a.getEnum("obstacles", Obstacles.class));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, PropertyValues.defaults(Grid16Arena.INSTANCE));
        assertEquals(new BoolValue(false), PropertyValues.defaults(FourWayControl.INSTANCE).get("diagonals"));
    }
}
