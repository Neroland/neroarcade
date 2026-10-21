package za.co.neroland.neroarcade.bench;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.ArcadeSchema;
import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.GameDefCodecs;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;

/**
 * The game a bench is building: per rack slot, the module whose chip sits there and the property values set for
 * it; the title; and, after a LOAD or SAVE, the cartridge it came from. Pure (no world, no items): the bench block
 * entity feeds it the chip ids from its container.
 *
 * <p><b>Invariants:</b> a property value belongs to the chip that was in the slot when it was set — replacing the
 * chip clears it. Values are stored only when they differ from the module's default (the canonical form the
 * validator writes). {@link #preview} never carries an author UUID. Saving keeps the id and bumps the revision
 * only for the same author's own cartridge; anyone else's LOAD then SAVE is a remix: a new id, the parent appended
 * to the lineage (002 §2.2).
 */
public final class BenchDraft {

    /** A valid id for drafts that have never been saved (the preview only). */
    public static final String PLACEHOLDER_ID = "aaaaaaaaaaaaaaaa";
    /** The preview title before the builder types one. */
    public static final String PLACEHOLDER_TITLE = "Untitled";

    /** Where the draft came from: empty for a new game. */
    public record Meta(Optional<String> id, List<String> lineage, int revision, Optional<UUID> author,
            Optional<String> credit, Optional<Long> seed, long createdAt) {

        public static final Meta NONE = new Meta(Optional.empty(), List.of(), 0, Optional.empty(), Optional.empty(),
                Optional.empty(), 0L);

        public static final Codec<Meta> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("id").forGetter(Meta::id),
                Codec.STRING.listOf().optionalFieldOf("lineage", List.of()).forGetter(Meta::lineage),
                Codec.INT.optionalFieldOf("revision", 0).forGetter(Meta::revision),
                GameDefCodecs.UUID_STRING.optionalFieldOf("author").forGetter(Meta::author),
                Codec.STRING.optionalFieldOf("credit").forGetter(Meta::credit),
                Codec.LONG.optionalFieldOf("seed").forGetter(Meta::seed),
                Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(Meta::createdAt)
        ).apply(i, Meta::new));

        public Meta {
            lineage = List.copyOf(lineage);
        }

        static Meta of(GameDef def) {
            return new Meta(Optional.of(def.id()), def.lineage(), def.revision(), def.authorUuid(), def.authorCredit(),
                    def.seed(), def.createdAt());
        }
    }

    /** One rack slot's property values, for persistence. */
    private record SlotProps(int index, Identifier owner, Map<String, PropertyValue> values) {
        static final Codec<SlotProps> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(SlotProps::index),
                Identifier.CODEC.fieldOf("module").forGetter(SlotProps::owner),
                Codec.unboundedMap(Codec.STRING, PropertyValue.RAW_CODEC).fieldOf("props").forGetter(SlotProps::values)
        ).apply(i, SlotProps::new));
    }

    private record Stored(String title, Meta meta, List<SlotProps> props) {
    }

    /** Persistent form for the bench block entity. */
    public static final Codec<BenchDraft> CODEC = RecordCodecBuilder.<Stored>create(i -> i.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(Stored::title),
            Meta.CODEC.optionalFieldOf("meta", Meta.NONE).forGetter(Stored::meta),
            SlotProps.CODEC.listOf().optionalFieldOf("props", List.of()).forGetter(Stored::props)
    ).apply(i, Stored::new)).xmap(BenchDraft::fromStored, BenchDraft::toStored);

    private final Identifier[] owners = new Identifier[BenchSlots.CHIP_SLOTS];
    @SuppressWarnings("unchecked")
    private final Map<String, PropertyValue>[] props = new Map[BenchSlots.CHIP_SLOTS];
    private String title = "";
    private Meta meta = Meta.NONE;

    public BenchDraft() {
        for (int i = 0; i < props.length; i++) {
            props[i] = new LinkedHashMap<>();
        }
    }

    // --- chips and properties ---------------------------------------------------------------------

    /**
     * Brings the draft in line with the rack: a slot whose chip changed loses its property values.
     *
     * @param chips module id per rack slot (null = empty), length {@link BenchSlots#CHIP_SLOTS}
     * @return true if anything changed
     */
    public boolean syncChips(Identifier[] chips) {
        boolean changed = false;
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            Identifier chip = i < chips.length ? chips[i] : null;
            if (!Objects.equals(chip, owners[i])) {
                owners[i] = chip;
                props[i].clear();
                changed = true;
            }
        }
        return changed;
    }

    /** The module id at a rack slot (null = empty). */
    public Identifier chipAt(int index) {
        return owners[index];
    }

    /**
     * A SET_PROP edit from the bench: INT uses {@code intValue} clamped to the spec's range; BOOL uses
     * {@code boolValue}; ENUM uses {@code intValue} as the constant index, clamped. An unknown property, an empty
     * slot or a module this server lacks is ignored.
     *
     * @return true if the draft changed
     */
    public boolean setProp(int index, String name, int intValue, boolean boolValue) {
        if (!BenchSlots.isChipSlot(index) || owners[index] == null) {
            return false;
        }
        Optional<ArcadeModule> module = ModuleRegistry.get(owners[index]);
        if (module.isEmpty()) {
            return false;
        }
        for (PropertySpec spec : module.get().properties()) {
            if (spec.name().equals(name)) {
                PropertyValue value = clamp(spec, intValue, boolValue);
                PropertyValue before = props[index].get(name);
                if (value.equals(spec.defaultValue())) {
                    props[index].remove(name);
                } else {
                    props[index].put(name, value);
                }
                return !Objects.equals(before, props[index].get(name));
            }
        }
        return false;
    }

    /** The edit value as the spec's kind, always inside its range. */
    public static PropertyValue clamp(PropertySpec spec, int intValue, boolean boolValue) {
        return switch (spec.kind()) {
            case INT -> new PropertyValue.IntValue(Math.max(spec.min(), Math.min(spec.max(), intValue)));
            case BOOL -> new PropertyValue.BoolValue(boolValue);
            case ENUM -> new PropertyValue.EnumValue(
                    spec.constants().get(Math.max(0, Math.min(spec.constants().size() - 1, intValue))));
        };
    }

    /** The explicit (non-default) values at a rack slot. */
    public Map<String, PropertyValue> propsAt(int index) {
        return Map.copyOf(props[index]);
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public Meta meta() {
        return meta;
    }

    // --- building ---------------------------------------------------------------------------------

    /** Slots from the rack: each type's chips in stack order; an empty type is absent (= its default). */
    public Map<ModuleType, List<SlotEntry>> slots() {
        EnumMap<ModuleType, List<SlotEntry>> out = new EnumMap<>(ModuleType.class);
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            if (owners[i] != null) {
                out.computeIfAbsent(BenchSlots.typeAt(i), t -> new ArrayList<>())
                        .add(new SlotEntry(owners[i], Map.copyOf(props[i])));
            }
        }
        return out;
    }

    /** What the preview runs: never an author UUID; placeholder id and title until the first save. */
    public GameDef preview() {
        return new GameDef(ArcadeSchema.CURRENT, meta.id().orElse(PLACEHOLDER_ID),
                title.isBlank() ? PLACEHOLDER_TITLE : title, Optional.empty(), meta.credit(), meta.lineage(),
                meta.seed(), slots(), meta.createdAt(), Math.max(1, meta.revision()));
    }

    /**
     * The cartridge a SAVE by {@code player} writes. Same author's own cartridge: same id, revision + 1. Someone
     * else's (a remix): a new id, the parent appended to the lineage, revision 1, no fixed seed. A new game: a new
     * id, revision 1.
     *
     * @throws ValidationError {@code BAD_TITLE} when there is no title yet
     */
    public GameDef planSave(UUID player, Supplier<String> mintId, long nowEpochSeconds, Optional<String> credit)
            throws ValidationError {
        if (title.isBlank()) {
            throw new ValidationError(ValidationError.Code.BAD_TITLE);
        }
        boolean own = meta.id().isPresent() && (meta.author().isEmpty() || meta.author().get().equals(player));
        String id;
        List<String> lineage;
        int revision;
        Optional<Long> seed;
        if (own) {
            id = meta.id().get();
            lineage = meta.lineage();
            revision = meta.revision() + 1;
            seed = meta.seed();
        } else {
            id = mintId.get();
            lineage = meta.id().isPresent() ? CartridgeIds.remixLineage(meta.lineage(), meta.id().get()) : List.of();
            revision = 1;
            seed = Optional.empty();
        }
        return new GameDef(ArcadeSchema.CURRENT, id, title, Optional.of(player), credit, lineage, seed, slots(),
                nowEpochSeconds, revision);
    }

    /** After a successful save: the draft now edits that cartridge. */
    public void adopt(GameDef saved) {
        meta = Meta.of(saved);
        title = saved.title();
    }

    // --- loading ----------------------------------------------------------------------------------

    /** A LOAD outcome: the draft, or why the cartridge is unreadable here (never a crash). */
    public record Load(Optional<BenchDraft> draft, Optional<ValidationError> error) {
    }

    /** Reads a cartridge into a fresh draft. Unknown modules, newer schemas and bad data come back as {@code error}. */
    public static Load load(StoredCartridge stored) {
        try {
            GameDef def = stored.decode();
            return new Load(Optional.of(fromGameDef(def)), Optional.empty());
        } catch (ValidationError refused) {
            return new Load(Optional.empty(), Optional.of(refused));
        }
    }

    /** A draft whose rack, values, title and origin are {@code def}'s (stack entries packed from position 0). */
    public static BenchDraft fromGameDef(GameDef def) {
        BenchDraft draft = new BenchDraft();
        for (ModuleType type : ModuleType.values()) {
            List<SlotEntry> entries = def.slot(type);
            for (int s = 0; s < entries.size() && s < type.maxStack; s++) {
                int index = BenchSlots.index(type, s);
                draft.owners[index] = entries.get(s).id();
                draft.props[index].putAll(entries.get(s).props());
            }
        }
        draft.title = def.title();
        draft.meta = Meta.of(def);
        return draft;
    }

    /** Copies another draft's state into this one (the bench keeps one instance). */
    public void copyFrom(BenchDraft other) {
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            owners[i] = other.owners[i];
            props[i].clear();
            props[i].putAll(other.props[i]);
        }
        title = other.title;
        meta = other.meta;
    }

    // --- persistence ------------------------------------------------------------------------------

    private static BenchDraft fromStored(Stored stored) {
        BenchDraft draft = new BenchDraft();
        draft.title = stored.title();
        draft.meta = stored.meta();
        for (SlotProps slot : stored.props()) {
            if (BenchSlots.isChipSlot(slot.index())) {
                draft.owners[slot.index()] = slot.owner();
                draft.props[slot.index()].putAll(slot.values());
            }
        }
        return draft;
    }

    private Stored toStored() {
        List<SlotProps> list = new ArrayList<>();
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            if (owners[i] != null) {
                list.add(new SlotProps(i, owners[i], Map.copyOf(props[i])));
            }
        }
        return new Stored(title, meta, list);
    }
}
