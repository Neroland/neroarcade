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
    /** {@code /neroarcade admin unseat}. The running match records nothing for this seat (compliance review F1). */
    ADMIN,
    /** The player's data was erased ({@code /neroland data eraseme}); the match records nothing for them. */
    ERASED,
    /** The bus lost its host (Stage 4). */
    HOST_GONE,
    /** No power for {@code sleepAfterTicks}. */
    POWER,
    /** The cabinet fell asleep or its chunk unloaded. */
    SLEEP,
    /** The host's tick threw past its own runtime guard and the host was shut down (final review H1). */
    FAULT;

    public String langKey() {
        return "neroarcade.seat.dropped." + name().toLowerCase(Locale.ROOT);
    }
}
