package za.co.neroland.neroarcade.cabinet;

import org.jetbrains.annotations.Nullable;

/**
 * A block entity a player can sit at: the arcade cabinet, or the game bench during a test play. The packet
 * handlers and {@code /neroarcade admin unseat} reach seats only through this interface.
 */
public interface SeatHolder {

    /** The match this holder runs (solo: its own host), or null while asleep. */
    @Nullable
    MatchHost matchHost();

    /** The seat at this holder, or null. */
    @Nullable
    Seat heldSeat();

    /** Unbinds the seat here (no-op without one) and tells the player why unless {@code why} is ESC. */
    void dropSeat(DropReason why);

    /** Counts an input packet addressed here by someone who holds no seat here (spectator, wrong player). */
    void countRejectedInput();
}
