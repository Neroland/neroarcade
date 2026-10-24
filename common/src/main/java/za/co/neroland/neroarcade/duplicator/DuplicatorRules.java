package za.co.neroland.neroarcade.duplicator;

import java.util.Optional;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * The Cartridge Duplicator's rules (003 §7), pure. A copy is a copy: the output's {@link GameDef} is the original's,
 * unchanged — the same id (so copies pool onto one leaderboard), lineage, revision, author UUID and credit (the
 * credit was the author's choice when they saved, and this is their game). There is no copy limit: copying a data
 * item is not an exploit.
 */
public final class DuplicatorRules {

    /** Ticks one copy takes (3 seconds). */
    public static final int COPY_TICKS = 60;
    /** FE one copy would draw at its start once the duplicator has an energy buffer (a later release; unused). */
    public static final int COPY_ENERGY = 200;

    /** Why COPY cannot start, as the one sentence the screen shows ({@code neroarcade.duplicator.deny.*}). */
    public enum Deny {
        NEED_ORIGINAL,
        NEED_BLANK,
        UNREADABLE,
        OUTPUT_FULL,
        NO_POWER,
        BUSY;

        public String langKey() {
            return "neroarcade.duplicator.deny." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private DuplicatorRules() {
    }

    /**
     * Whether COPY may start.
     *
     * @param original  the original's game, or empty when the original slot holds no readable cartridge
     * @param hasOriginal a written cartridge is in the original slot (readable or not)
     * @param hasBlank  a Blank Cartridge is in the blank slot
     * @param outputsFree both output slots are empty
     * @param powered   power is available (always true when cabinets need no power)
     * @param copying   a copy is already running
     */
    public static Optional<Deny> check(Optional<GameDef> original, boolean hasOriginal, boolean hasBlank,
            boolean outputsFree, boolean powered, boolean copying) {
        if (copying) {
            return Optional.of(Deny.BUSY);
        }
        if (!hasOriginal) {
            return Optional.of(Deny.NEED_ORIGINAL);
        }
        if (original.isEmpty()) {
            return Optional.of(Deny.UNREADABLE);
        }
        if (!hasBlank) {
            return Optional.of(Deny.NEED_BLANK);
        }
        if (!outputsFree) {
            return Optional.of(Deny.OUTPUT_FULL);
        }
        if (!powered) {
            return Optional.of(Deny.NO_POWER);
        }
        return Optional.empty();
    }

    /** The copy's game: the original's, unchanged. */
    public static GameDef copyOf(GameDef original) {
        return original;
    }
}
