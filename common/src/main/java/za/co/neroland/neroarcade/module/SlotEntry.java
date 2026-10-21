package za.co.neroland.neroarcade.module;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
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

    public static final Codec<SlotEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(SlotEntry::id),
            Codec.unboundedMap(Codec.STRING, PropertyValue.RAW_CODEC).optionalFieldOf("props", Map.of())
                    .forGetter(SlotEntry::props)
    ).apply(i, SlotEntry::new));

    public SlotEntry {
        props = props == null || props.isEmpty() ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(props));
    }

    /** An entry with no explicit properties. */
    public static SlotEntry of(Identifier id) {
        return new SlotEntry(id, Map.of());
    }
}
