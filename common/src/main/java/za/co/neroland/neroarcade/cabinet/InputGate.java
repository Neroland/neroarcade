package za.co.neroland.neroarcade.cabinet;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * Input acceptance for {@code SeatInputPayload} (003 §2.4–2.5). Pure: the packet handler resolves the seat at
 * the addressed cabinet and the sender's UUID from the connection, never from the payload.
 *
 * <p>Order: (1) the cabinet must hold a seat and its UUID must equal the sender's; (2) the packet's match id
 * must equal the host's; (3) a fixed one-second window per seat ({@code inputsThisSecond}, reset after 20
 * game ticks). Every refusal is silent and counted; nothing here ever unseats or kicks.
 */
public final class InputGate {

    /** Ticks in one rate window. */
    public static final int WINDOW_TICKS = 20;

    public enum Verdict {
        ACCEPTED,
        /** No seat at that cabinet (a spectator, or nobody). Counted on the cabinet. */
        NO_SEAT,
        /** The seat belongs to someone else. Counted on the cabinet, not on the victim's seat. */
        WRONG_PLAYER,
        /** A packet from a previous match. Counted on the seat. */
        STALE_MATCH,
        /** Over {@code runtime.maxInputPacketsPerSecond}. Counted on the seat. */
        RATE_LIMITED;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    private InputGate() {
    }

    /**
     * Decides one packet and, when accepted, latches {@code input} on the seat. Counters: a refusal against an
     * existing seat owned by the sender increments {@link Seat#droppedInputs}; the caller counts
     * {@link Verdict#NO_SEAT} / {@link Verdict#WRONG_PLAYER} on the cabinet.
     */
    public static Verdict offer(@Nullable Seat seat, UUID sender, int packetMatchId, int hostMatchId,
            SeatInput input, long gameTime, int maxPerSecond) {
        if (seat == null) {
            return Verdict.NO_SEAT;
        }
        if (!seat.playerUuid.equals(sender)) {
            return Verdict.WRONG_PLAYER;
        }
        if (packetMatchId != hostMatchId) {
            seat.droppedInputs++;
            return Verdict.STALE_MATCH;
        }
        if (seat.secondStartedAt == Long.MIN_VALUE || gameTime - seat.secondStartedAt >= WINDOW_TICKS
                || gameTime < seat.secondStartedAt) {
            seat.secondStartedAt = gameTime;
            seat.inputsThisSecond = 0;
        }
        if (seat.inputsThisSecond >= maxPerSecond) {
            seat.droppedInputs++;
            return Verdict.RATE_LIMITED;
        }
        seat.inputsThisSecond++;
        seat.latched = input == null ? SeatInput.NONE : input;
        seat.lastInputTick = gameTime;
        return Verdict.ACCEPTED;
    }
}
