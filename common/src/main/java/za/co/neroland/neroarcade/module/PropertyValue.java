package za.co.neroland.neroarcade.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

/**
 * One property value: an int, a bool or an enum constant. Never a float, a list or an object.
 *
 * <p>{@link #RAW_CODEC} decodes whatever primitive the cartridge holds without consulting the module's spec;
 * {@link PropertySpec#coerce} then checks it against the spec. NBT cannot tell a bool from a byte, so an NBT
 * bool reads back as {@code IntValue(0|1)} and coercion turns it into a BOOL. A number that is not an exact
 * 32-bit integer (e.g. {@code 1.5}) is a decode error: there are no fractional properties.
 */
public sealed interface PropertyValue permits PropertyValue.IntValue, PropertyValue.BoolValue, PropertyValue.EnumValue {

    PropertyKind kind();

    record IntValue(int value) implements PropertyValue {
        @Override
        public PropertyKind kind() {
            return PropertyKind.INT;
        }
    }

    record BoolValue(boolean value) implements PropertyValue {
        @Override
        public PropertyKind kind() {
            return PropertyKind.BOOL;
        }
    }

    record EnumValue(String constant) implements PropertyValue {
        @Override
        public PropertyKind kind() {
            return PropertyKind.ENUM;
        }
    }

    /**
     * Int, bool or string, flattened into a {@code PropertyValue}. The encoder writes INT as an int tag, BOOL
     * as a byte tag (a JSON boolean) and ENUM as its lowercase string. Equivalent to the contract's
     * {@code either(INT, either(BOOL, STRING))}, but it inspects the primitive's real type so a JSON boolean
     * stays a BOOL and a JSON {@code 1.5} is refused instead of being truncated.
     */
    PrimitiveCodec<PropertyValue> RAW_CODEC = new PrimitiveCodec<>() {
        @Override
        public <T> DataResult<PropertyValue> read(DynamicOps<T> ops, T input) {
            JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
            if (!(json instanceof JsonPrimitive primitive)) {
                return DataResult.error(() -> "property value must be an int, a bool or a string: " + json);
            }
            if (primitive.isBoolean()) {
                return DataResult.success(new BoolValue(primitive.getAsBoolean()));
            }
            if (primitive.isString()) {
                return DataResult.success(new EnumValue(primitive.getAsString()));
            }
            String text = primitive.getAsString();
            try {
                return DataResult.success(new IntValue(Integer.parseInt(text)));
            } catch (NumberFormatException notAnInt) {
                return DataResult.error(() -> "property value is not a 32-bit integer: " + text);
            }
        }

        @Override
        public <T> T write(DynamicOps<T> ops, PropertyValue value) {
            return switch (value) {
                case IntValue i -> ops.createInt(i.value());
                case BoolValue b -> ops.createBoolean(b.value());
                case EnumValue e -> ops.createString(e.constant());
            };
        }

        @Override
        public String toString() {
            return "PropertyValue";
        }
    };
}
