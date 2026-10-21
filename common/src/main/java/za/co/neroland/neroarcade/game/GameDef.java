package za.co.neroland.neroarcade.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;

/**
 * A cartridge: one game, as data. Behaviour lives in modules; a cartridge only names them and their
 * parameters, so it is small, versioned and safe to share.
 *
 * <p><b>Invariants:</b> immutable; {@code slots} is keyed in {@link ModuleType} order with no empty lists (an
 * absent key is an empty slot, resolved to the server default at play time, never written into the
 * cartridge). A {@code GameDef} is not trusted until {@link CartridgeValidator} has accepted it: the runtime
 * only ever sees a {@link ResolvedGame}. {@code authorCredit} is present only when the author opted in, and
 * is never a UUID.
 *
 * @param id        16-char lowercase base32 ({@link CartridgeIds}); lineage key and deep-link id
 * @param lineage   ancestor ids, oldest first, at most 8; {@code lineage[0]} is the root
 * @param seed      fixed match seed (gallery replays, daily boards); empty = server random per match
 * @param createdAt epoch seconds, informational only (never read by the simulation)
 * @param revision  1 at first save, +1 per bench save
 */
public record GameDef(int schemaVersion, String id, String title, Optional<UUID> authorUuid,
        Optional<String> authorCredit, List<String> lineage, Optional<Long> seed,
        Map<ModuleType, List<SlotEntry>> slots, long createdAt, int revision) {

    /**
     * Field names {@code schema, id, title, author, author_credit, lineage, seed, slots, created_at, revision};
     * {@code author}, {@code author_credit} and {@code seed} are optional (absent = empty).
     */
    public static final Codec<GameDef> CODEC = GameDefCodecs.GAME_DEF;

    public GameDef {
        authorUuid = authorUuid == null ? Optional.empty() : authorUuid;
        authorCredit = authorCredit == null ? Optional.empty() : authorCredit;
        seed = seed == null ? Optional.empty() : seed;
        lineage = lineage == null ? List.of() : List.copyOf(lineage);
        EnumMap<ModuleType, List<SlotEntry>> sorted = new EnumMap<>(ModuleType.class);
        if (slots != null) {
            for (Map.Entry<ModuleType, List<SlotEntry>> entry : slots.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    sorted.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
        }
        slots = Collections.unmodifiableMap(sorted);
    }

    /** The lineage root: {@code lineage[0]}, or this cartridge's own id when it has no ancestors. */
    public String lineageRoot() {
        return lineage.isEmpty() ? id : lineage.get(0);
    }

    /** The entries in one slot (empty list = empty slot). */
    public List<SlotEntry> slot(ModuleType type) {
        return slots.getOrDefault(type, List.of());
    }

    /** A copy with one slot replaced (empty list clears it). */
    public GameDef withSlot(ModuleType type, List<SlotEntry> entries) {
        EnumMap<ModuleType, List<SlotEntry>> copy = new EnumMap<>(ModuleType.class);
        copy.putAll(slots);
        copy.put(type, new ArrayList<>(entries));
        return new GameDef(schemaVersion, id, title, authorUuid, authorCredit, lineage, seed, copy, createdAt, revision);
    }
}
