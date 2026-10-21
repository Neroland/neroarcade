package za.co.neroland.neroarcade.module;

import java.util.List;
import java.util.regex.Pattern;

import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;

/**
 * The declaration of one module property: name, kind, default and (INT) range or (ENUM) constants.
 *
 * <p><b>Invariants</b> (checked in the constructor, so a bad spec fails at registration, not at play):
 * name and every constant match {@code [a-z][a-z0-9_]{0,15}}; the default's kind equals {@link #kind};
 * INT has {@code min <= default <= max}; ENUM has 2..8 distinct constants and the default is one of them.
 * Coercion fails closed: an out-of-range INT or an unknown constant is {@code bad_property}, never a clamp,
 * because a cartridge is a document.
 *
 * @param min       INT only, inclusive; 0 otherwise
 * @param max       INT only, inclusive; 0 otherwise
 * @param constants ENUM only; empty otherwise
 */
public record PropertySpec(String name, PropertyKind kind, PropertyValue defaultValue, int min, int max,
        List<String> constants) {

    /** Property names and enum constants: ASCII, lowercase, at most 16 characters. */
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,15}");

    public PropertySpec {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("bad property name: " + name);
        }
        if (kind == null || defaultValue == null || defaultValue.kind() != kind) {
            throw new IllegalArgumentException("property " + name + ": default must be a " + kind);
        }
        constants = constants == null ? List.of() : List.copyOf(constants);
        switch (kind) {
            case INT -> {
                int def = ((IntValue) defaultValue).value();
                if (min > def || def > max) {
                    throw new IllegalArgumentException("property " + name + ": need min <= default <= max");
                }
                if (!constants.isEmpty()) {
                    throw new IllegalArgumentException("property " + name + ": INT has no constants");
                }
            }
            case BOOL -> {
                if (min != 0 || max != 0 || !constants.isEmpty()) {
                    throw new IllegalArgumentException("property " + name + ": BOOL has no range or constants");
                }
            }
            case ENUM -> {
                if (min != 0 || max != 0) {
                    throw new IllegalArgumentException("property " + name + ": ENUM has no range");
                }
                if (constants.size() < 2 || constants.size() > 8 || constants.stream().distinct().count() != constants.size()) {
                    throw new IllegalArgumentException("property " + name + ": ENUM needs 2..8 distinct constants");
                }
                for (String constant : constants) {
                    if (!NAME.matcher(constant).matches()) {
                        throw new IllegalArgumentException("property " + name + ": bad constant " + constant);
                    }
                }
                if (!constants.contains(((EnumValue) defaultValue).constant())) {
                    throw new IllegalArgumentException("property " + name + ": default is not a constant");
                }
            }
        }
    }

    public static PropertySpec integer(String name, int def, int min, int max) {
        return new PropertySpec(name, PropertyKind.INT, new IntValue(def), min, max, List.of());
    }

    public static PropertySpec bool(String name, boolean def) {
        return new PropertySpec(name, PropertyKind.BOOL, new BoolValue(def), 0, 0, List.of());
    }

    public static PropertySpec enumeration(String name, String def, String... constants) {
        return new PropertySpec(name, PropertyKind.ENUM, new EnumValue(def), 0, 0, List.of(constants));
    }

    /** {@code module.neroarcade.pawn.cursor.prop.speed} = "Speed". */
    public String langKey(ArcadeModule owner) {
        return owner.langKey() + ".prop." + name;
    }

    /** {@code ...prop.axis.horizontal} = "Horizontal". */
    public String constantLangKey(ArcadeModule owner, String constant) {
        return langKey(owner) + "." + constant;
    }

    /**
     * Checks a raw decoded value against this spec. Accepts {@code IntValue(0|1)} for BOOL and a
     * {@code BoolValue} for an INT whose range is exactly {@code [0,1]} (NBT cannot tell them apart).
     *
     * @throws ValidationError {@code BAD_PROPERTY} with args {@code [name]} on any other mismatch
     */
    public PropertyValue coerce(PropertyValue raw) throws ValidationError {
        switch (kind) {
            case INT -> {
                if (raw instanceof IntValue i && i.value() >= min && i.value() <= max) {
                    return i;
                }
                if (raw instanceof BoolValue b && min == 0 && max == 1) {
                    return new IntValue(b.value() ? 1 : 0);
                }
            }
            case BOOL -> {
                if (raw instanceof BoolValue b) {
                    return b;
                }
                if (raw instanceof IntValue i && (i.value() == 0 || i.value() == 1)) {
                    return new BoolValue(i.value() == 1);
                }
            }
            case ENUM -> {
                if (raw instanceof EnumValue e && constants.contains(e.constant())) {
                    return e;
                }
            }
        }
        throw new ValidationError(ValidationError.Code.BAD_PROPERTY, name);
    }
}
