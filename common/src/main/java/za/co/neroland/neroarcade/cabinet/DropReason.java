package za.co.neroland.neroarcade.cabinet;

import java.util.Locale;

/** Why a seat was unbound (003 §2.3). Each has the message key {@code neroarcade.seat.dropped.<name>}. */
public enum DropReason {
    /** The player pressed Esc ({@code LeaveSeatPayload}). */
    ESC,
    /** Walked out of {@code seatRange} or changed dimension. */
    RANGE,
    LOGOUT,
    /** The cabinet (or bench) was broken, or its cartridge ejected. */
    BROKEN,
    /** {@code /neroarcade admin unseat}. */
    ADMIN,
    /** The bus lost its host (Stage 4). */
    HOST_GONE,
    /** No power for {@code sleepAfterTicks}. */
    POWER,
    /** The cabinet fell asleep or its chunk unloaded. */
    SLEEP;

    public String langKey() {
        return "neroarcade.seat.dropped." + name().toLowerCase(Locale.ROOT);
    }
}
