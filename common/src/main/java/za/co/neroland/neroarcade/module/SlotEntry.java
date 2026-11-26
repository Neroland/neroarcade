package za.co.neroland.neroarcade.module;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
//? if <1.20.5 {
/*import java.util.stream.Stream;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
*///?}
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One chip in one slot of a cartridge: the module id plus its explicit property values, raw (not yet
 * spec-coerced). Serialised as {@code {"id": "neroarcade:pawn/cursor", "props": {"speed": 4}}}.
 *
 * <p><b>Invariants:</b> immutable; {@code props} holds explicit values only (defaults are applied on read by
 * {@link PropertyValues#of}); an empty {@code props} is omitted on write. {@code props} is stored sorted by
 * name so equality and the encoded order never depend on map iteration order.
 */
public record SlotEntry(Identifier id, Map<String, PropertyValue> props) {

    private static final Codec<Map<String, PropertyValue>> PROPERTIES = Codec.unboundedMap(Codec.STRING, PropertyValue.RAW_CODEC);

    //? if >=1.20.5 {
    private static final MapCodec<Map<String, PropertyValue>> PROPS = PROPERTIES.optionalFieldOf("props", Map.of());
    //?} else {
    /*private static final MapCodec<Map<String, PropertyValue>> PRESENT_PROPS = PROPERTIES.fieldOf("props");

    // The older DFU optionalFieldOf discards invalid values. Only an absent props field may use the default.
    private static final MapCodec<Map<String, PropertyValue>> PROPS = new MapCodec<>() {
        @Override
        public <T> DataResult<Map<String, PropertyValue>> decode(DynamicOps<T> ops, MapLike<T> input) {
            return input.get("props") == null ? DataResult.success(Map.of()) : PRESENT_PROPS.decode(ops, input);
        }

        @Override
        public <T> RecordBuilder<T> encode(Map<String, PropertyValue> props, DynamicOps<T> ops, RecordBuilder<T> output) {
            return props.isEmpty() ? output : PRESENT_PROPS.encode(props, ops, output);
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return PRESENT_PROPS.keys(ops);
        }
    };
    *///?}

    public static final Codec<SlotEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(SlotEntry::id),
            PROPS.forGetter(SlotEntry::props)
    ).apply(i, SlotEntry::new));

    public SlotEntry {
        props = props == null || props.isEmpty() ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(props));
    }

    /** An entry with no explicit properties. */
    public static SlotEntry of(Identifier id) {
        return new SlotEntry(id, Map.of());
    }
}
