package za.co.neroland.neroarcade.module;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;

/**
 * A module's fully resolved properties: every declared property has a value, coerced and validated against
 * its {@link PropertySpec}.
 *
 * <p><b>Invariants:</b> immutable; iteration order is the module's declaration order; {@code equals} is by
 * content (names and values); every value satisfies its spec. A getter asked for an undeclared name or the
 * wrong kind throws {@link IllegalArgumentException} — that is a programming error in a chip, never a data
 * error. Unknown property names in a cartridge are ignored here (logged at debug) and surfaced as a bench
 * warning by the validator.
 */
public final class PropertyValues {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeroArcade");

    private final List<PropertySpec> specs;
    private final PropertyValue[] values;

    private PropertyValues(List<PropertySpec> specs, PropertyValue[] values) {
        this.specs = specs;
        this.values = values;
    }

    /** Every property at its default. */
    public static PropertyValues defaults(ArcadeModule module) {
        List<PropertySpec> specs = List.copyOf(module.properties());
        PropertyValue[] values = new PropertyValue[specs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = specs.get(i).defaultValue();
        }
        return new PropertyValues(specs, values);
    }

    /**
     * Validates and coerces the explicit values, fills the rest with defaults. Names the module does not
     * declare are ignored.
     *
     * @throws ValidationError {@code BAD_PROPERTY} with args {@code [property, module id]}
     */
    public static PropertyValues of(ArcadeModule module, Map<String, PropertyValue> explicit) throws ValidationError {
        PropertyValues result = defaults(module);
        for (int i = 0; i < result.specs.size(); i++) {
            PropertySpec spec = result.specs.get(i);
            PropertyValue raw = explicit.get(spec.name());
            if (raw != null) {
                try {
                    result.values[i] = spec.coerce(raw);
                } catch (ValidationError bad) {
                    throw new ValidationError(ValidationError.Code.BAD_PROPERTY, spec.name(), module.id().toString());
                }
            }
        }
        for (String name : explicit.keySet()) {
            if (result.indexOf(name) < 0) {
                LOGGER.debug("[NeroArcade] ignoring undeclared property {} on {}", name, module.id());
            }
        }
        return result;
    }

    /** True when {@code explicit} names a property the module does not declare. */
    public static boolean hasUnknown(ArcadeModule module, Map<String, PropertyValue> explicit) {
        for (String name : explicit.keySet()) {
            boolean declared = false;
            for (PropertySpec spec : module.properties()) {
                declared |= spec.name().equals(name);
            }
            if (!declared) {
                return true;
            }
        }
        return false;
    }

    public int getInt(String name) {
        return ((IntValue) value(name, PropertyKind.INT)).value();
    }

    public boolean getBool(String name) {
        return ((BoolValue) value(name, PropertyKind.BOOL)).value();
    }

    public String getEnum(String name) {
        return ((EnumValue) value(name, PropertyKind.ENUM)).constant();
    }

    /** {@code constant.toUpperCase(ROOT)} → {@code E.valueOf}; a convenience for chip authors. */
    public <E extends Enum<E>> E getEnum(String name, Class<E> type) {
        return Enum.valueOf(type, getEnum(name).toUpperCase(Locale.ROOT));
    }

    /** The value of a declared property, whatever its kind. */
    public PropertyValue get(String name) {
        int index = indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("undeclared property: " + name);
        }
        return values[index];
    }

    /** Only the values that differ from their default, in declaration order — what the codec writes. */
    public Map<String, PropertyValue> explicit() {
        Map<String, PropertyValue> out = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (!values[i].equals(specs.get(i).defaultValue())) {
                out.put(specs.get(i).name(), values[i]);
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /**
     * A copy with one property changed (the bench's slider edit).
     *
     * @throws ValidationError {@code BAD_PROPERTY} if the value does not satisfy the spec
     * @throws IllegalArgumentException if the name is not declared
     */
    public PropertyValues with(String name, PropertyValue value) throws ValidationError {
        int index = indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("undeclared property: " + name);
        }
        PropertyValue[] copy = values.clone();
        copy[index] = specs.get(index).coerce(value);
        return new PropertyValues(specs, copy);
    }

    /** The specs these values satisfy, in declaration order. */
    public List<PropertySpec> specs() {
        return specs;
    }

    private PropertyValue value(String name, PropertyKind kind) {
        int index = indexOf(name);
        if (index < 0 || specs.get(index).kind() != kind) {
            throw new IllegalArgumentException("property " + name + " is not declared as " + kind);
        }
        return values[index];
    }

    private int indexOf(String name) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PropertyValues that) || that.specs.size() != specs.size()) {
            return false;
        }
        for (int i = 0; i < specs.size(); i++) {
            if (!specs.get(i).name().equals(that.specs.get(i).name())) {
                return false;
            }
        }
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = 0; i < specs.size(); i++) {
            hash = 31 * hash + Objects.hash(specs.get(i).name(), values[i]);
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < specs.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(specs.get(i).name()).append('=').append(values[i]);
        }
        return out.append('}').toString();
    }
}
