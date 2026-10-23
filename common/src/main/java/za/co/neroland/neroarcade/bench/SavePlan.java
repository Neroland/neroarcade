package za.co.neroland.neroarcade.bench;

import java.util.Optional;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * The outcome of {@link BenchDraft#planSave}: what SAVE writes, or why it refuses. Pure value.
 *
 * <p><b>Invariant:</b> {@link #def} is present exactly when {@link #kind} is {@link Kind#NEW_CARTRIDGE} or
 * {@link Kind#IN_PLACE}; a NEW_CARTRIDGE def always carries a freshly minted id, never the draft's.
 */
public record SavePlan(Kind kind, Optional<GameDef> def) {

    public enum Kind {
        /** A Blank Cartridge becomes this cartridge: new id, revision 1, author = saver. */
        NEW_CARTRIDGE,
        /** The saver's own cartridge in the tray is rewritten: same id, revision + 1. */
        IN_PLACE,
        /** The tray holds the draft's cartridge, but it is not the saver's (or has no author). */
        REFUSED_NOT_YOURS,
        /** The tray holds a written cartridge that is not the draft's game. */
        REFUSED_OTHER_CARTRIDGE;

        /** The bench status key for a refusal; empty for a save. */
        public String statusKey() {
            return switch (this) {
                case REFUSED_NOT_YOURS -> "neroarcade.bench.status.not_yours";
                case REFUSED_OTHER_CARTRIDGE -> "neroarcade.bench.status.other_cartridge";
                default -> "";
            };
        }
    }

    public SavePlan {
        if (def.isPresent() != (kind == Kind.NEW_CARTRIDGE || kind == Kind.IN_PLACE)) {
            throw new IllegalArgumentException("a save carries a def, a refusal does not");
        }
    }

    static SavePlan refused(Kind kind) {
        return new SavePlan(kind, Optional.empty());
    }

    public boolean refused() {
        return def.isEmpty();
    }
}
