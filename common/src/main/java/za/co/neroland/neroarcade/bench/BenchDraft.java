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
 * validator writes). Taking a chip out and putting the same chip back restores its values (they are parked while
 * the slot is empty); any other chip clears them. {@link #preview} never carries an author UUID. A cartridge id is
 * unique: SAVE keeps the id and bumps the revision <b>only</b> when the tray holds that very cartridge and the draft's
 * recorded author is present and is the saver; every other SAVE goes onto a Blank and mints a new id, recording the
 * origin in the lineage (002 §2.2, review 005a F7). See {@link #planSave}.
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
    /** Per slot: the chip that left it (while the slot is empty) and its values, restored if it comes back. */
    private final Identifier[] parkedOwners = new Identifier[BenchSlots.CHIP_SLOTS];
    @SuppressWarnings("unchecked")
    private final Map<String, PropertyValue>[] parked = new Map[BenchSlots.CHIP_SLOTS];
    private String title = "";
    private Meta meta = Meta.NONE;

    public BenchDraft() {
        for (int i = 0; i < props.length; i++) {
            props[i] = new LinkedHashMap<>();
            parked[i] = new LinkedHashMap<>();
        }
    }

    // --- chips and properties ---------------------------------------------------------------------

    /**
     * Brings the draft in line with the rack. A slot that goes {@code id -> empty} parks its values under that id;
     * {@code empty -> id} with the parked id restores them (a take-out-and-put-back keeps the settings); any other
     * change clears both the values and the parked copy.
     *
     * @param chips module id per rack slot (null = empty), length {@link BenchSlots#CHIP_SLOTS}
     * @return true if anything changed
     */
    public boolean syncChips(Identifier[] chips) {
        boolean changed = false;
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            Identifier chip = i < chips.length ? chips[i] : null;
            if (!Objects.equals(chip, owners[i])) {
                if (chip == null) {
                    parkedOwners[i] = owners[i];
                    parked[i].clear();
                    parked[i].putAll(props[i]);
                    props[i].clear();
                } else if (owners[i] == null && chip.equals(parkedOwners[i])) {
                    props[i].clear();
                    props[i].putAll(parked[i]);
                    parkedOwners[i] = null;
                    parked[i].clear();
                } else {
                    props[i].clear();
                    parkedOwners[i] = null;
                    parked[i].clear();
                }
                owners[i] = chip;
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

    // --- privacy (Stage 6) ------------------------------------------------------------------------------

    /** Whether the draft's origin names {@code player} as author (the erasure probe for a loaded bench). */
    public boolean holdsAuthor(java.util.UUID player) {
        return player != null && meta.author().filter(player::equals).isPresent();
    }

    /**
     * Erasure (review 005a / 003b P2): if the draft's origin names {@code player} as author, the author becomes the
     * tombstone and the credit goes; id, lineage and the rack stay (the game itself is not personal data). A later
     * SAVE therefore mints a new id, as for any game the saver did not write.
     *
     * @return true if anything changed
     */
    public boolean eraseAuthor(java.util.UUID player) {
        if (!holdsAuthor(player)) {
            return false;
        }
        meta = new Meta(meta.id(), meta.lineage(), meta.revision(),
                Optional.of(za.co.neroland.neroarcade.privacy.Tombstones.ERASED_AUTHOR), Optional.empty(),
                meta.seed(), meta.createdAt());
        return true;
    }

    /**
     * Credit opt-out (compliance review F5): if the draft's origin names {@code player} as author and carries a
     * credit, the credit goes; the author, id and rack stay.
     *
     * @return true if anything changed
     */
    public boolean stripCredit(java.util.UUID player) {
        if (!holdsAuthor(player) || meta.credit().isEmpty()) {
            return false;
        }
        meta = new Meta(meta.id(), meta.lineage(), meta.revision(), meta.author(), Optional.empty(), meta.seed(),
                meta.createdAt());
        return true;
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

    /**
     * What the preview runs, and what the update tag and the bench screen receive: never an author UUID and never
     * the credit (compliance review F3: no client draws it there); placeholder id and title until the first save.
     */
    public GameDef preview() {
        return new GameDef(ArcadeSchema.CURRENT, meta.id().orElse(PLACEHOLDER_ID),
                title.isBlank() ? PLACEHOLDER_TITLE : title, Optional.empty(), Optional.empty(), meta.lineage(),
                meta.seed(), slots(), meta.createdAt(), Math.max(1, meta.revision()));
    }

    /**
     * What a SAVE by {@code player} does, given the tray (review 005a F7). Pure.
     *
     * <ul>
     *   <li><b>In place</b> (same id, revision + 1, lineage and seed kept) only when the tray holds a written
     *       cartridge whose id equals the draft's id <b>and</b> the draft's author is present and equals
     *       {@code player}. An authorless origin (gallery, fixtures, anything minted without a player) is never
     *       "own".</li>
     *   <li>A written cartridge in the tray otherwise is refused: {@link SavePlan.Kind#REFUSED_NOT_YOURS} when it is
     *       the draft's cartridge but not the saver's, {@link SavePlan.Kind#REFUSED_OTHER_CARTRIDGE} when it is a
     *       different game.</li>
     *   <li>A Blank in the tray ({@code trayId} empty) always gets a <b>new id</b>, revision 1, author = saver, and
     *       — when the draft came from a cartridge — that cartridge appended to the lineage
     *       ({@link CartridgeIds#remixLineage}, capped at {@link CartridgeIds#MAX_LINEAGE}, root kept), so a copy of
     *       one's own game shares its lineage root but never its id. The fixed seed survives only for the saver's
     *       own game.</li>
     * </ul>
     *
     * @param trayId the written cartridge's id in the tray, or empty for a Blank Cartridge
     * @throws ValidationError {@code BAD_TITLE} when there is no title yet
     */
    public SavePlan planSave(UUID player, Optional<String> trayId, Supplier<String> mintId, long nowEpochSeconds,
            Optional<String> credit) throws ValidationError {
        if (title.isBlank()) {
            throw new ValidationError(ValidationError.Code.BAD_TITLE);
        }
        boolean authoredBySaver = meta.author().isPresent() && meta.author().get().equals(player);
        if (trayId.isPresent()) {
            if (meta.id().isEmpty() || !trayId.get().equals(meta.id().get())) {
                return SavePlan.refused(SavePlan.Kind.REFUSED_OTHER_CARTRIDGE);
            }
            if (!authoredBySaver) {
                return SavePlan.refused(SavePlan.Kind.REFUSED_NOT_YOURS);
            }
            return new SavePlan(SavePlan.Kind.IN_PLACE, Optional.of(new GameDef(ArcadeSchema.CURRENT, meta.id().get(),
                    title, Optional.of(player), credit, meta.lineage(), meta.seed(), slots(), nowEpochSeconds,
                    meta.revision() + 1)));
        }
        String id = mintId.get();
        List<String> lineage = meta.id().isPresent() ? CartridgeIds.remixLineage(meta.lineage(), meta.id().get())
                : List.of();
        Optional<Long> seed = authoredBySaver ? meta.seed() : Optional.empty();
        return new SavePlan(SavePlan.Kind.NEW_CARTRIDGE, Optional.of(new GameDef(ArcadeSchema.CURRENT, id, title,
                Optional.of(player), credit, lineage, seed, slots(), nowEpochSeconds, 1)));
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
            parkedOwners[i] = null;
            parked[i].clear();
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
