package za.co.neroland.neroarcade.cabinet;

import java.util.UUID;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The one player standing at a cabinet (or a bench running a test play). Runtime-only: nothing here is written
 * to NBT, exported or logged, so a seat needs no erasure path (003 §1, §2.1).
 *
 * <p>Server thread only. {@link #seat} is the bus seat index (the solo cabinet is seat 0).
 */
public final class Seat {

    public final int seat;
    public final UUID playerUuid;
    /** Server game time at bind; informational ordering only. */
    public final long joinedAt;
    /** The block the player sits at (a bus node, the host, or a bench); null = the host itself. */
    public final @Nullable BlockPos at;

    boolean ready;
    long lastInputTick;
    SeatInput latched = SeatInput.NONE;
    int inputsThisSecond;
    long secondStartedAt = Long.MIN_VALUE;
    int droppedInputs;

    public Seat(int seat, UUID playerUuid, long joinedAt) {
        this(seat, playerUuid, joinedAt, null);
    }

    public Seat(int seat, UUID playerUuid, long joinedAt, @Nullable BlockPos at) {
        this.seat = seat;
        this.playerUuid = playerUuid;
        this.joinedAt = joinedAt;
        this.at = at == null ? null : at.immutable();
    }

    public boolean ready() {
        return ready;
    }

    /** The last accepted input (what the pad/button blockstate mirrors). */
    public SeatInput latched() {
        return latched;
    }

    /** Packets dropped for this seat (wrong match id, over the rate); a debugging counter, never logged at info. */
    public int droppedInputs() {
        return droppedInputs;
    }

    /**
     * Whether {@code seat} is held by {@code player} (e.g. the bench menu closing for the test player ends the test
     * on the server, review 005a F11). Pure.
     */
    public static boolean heldBy(@Nullable Seat seat, UUID player) {
        return seat != null && seat.playerUuid.equals(player);
    }
}
