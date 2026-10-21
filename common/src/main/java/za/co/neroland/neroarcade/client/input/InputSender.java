package za.co.neroland.neroarcade.client.input;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * Decides when the seated client sends a {@code SeatInputPayload}: on every change, plus a keep-alive of the
 * unchanged input at most {@link #KEEP_ALIVE_HZ} times a second, and never more than {@link #MAX_PER_SECOND} in
 * any {@link #WINDOW_TICKS}-tick window — below the server's per-seat budget of 30/s
 * ({@code runtime.maxInputPacketsPerSecond}, minimum 10), so a well-behaved client is never dropped. Polled once
 * per client tick (20 Hz), so in practice it sends at most 20 a second. Pure.
 */
public final class InputSender {

    public static final int KEEP_ALIVE_HZ = 5;
    /** Ticks between keep-alives (20 / 5). */
    public static final int KEEP_ALIVE_TICKS = 20 / KEEP_ALIVE_HZ;
    /** Hard cap per window; the server floor for {@code maxInputPacketsPerSecond} is 10, its default 30. */
    public static final int MAX_PER_SECOND = 20;
    public static final int WINDOW_TICKS = 20;

    private final long[] sentAt = new long[MAX_PER_SECOND];
    private int sentCount;
    private int sentHead;
    private @Nullable SeatInput last;
    private long lastSent = Long.MIN_VALUE / 2;
    private int lastMatchId = Integer.MIN_VALUE;

    /**
     * Polls once per tick.
     *
     * @return the input to send now, or null for nothing
     */
    public @Nullable SeatInput poll(SeatInput current, int matchId, long tick) {
        SeatInput now = current == null ? SeatInput.NONE : current;
        boolean changed = last == null || !last.equals(now) || matchId != lastMatchId;
        boolean keepAlive = tick - lastSent >= KEEP_ALIVE_TICKS;
        if (!changed && !keepAlive) {
            return null;
        }
        if (!underCap(tick)) {
            return null; // a later poll sends the newest state
        }
        record(tick);
        last = now;
        lastSent = tick;
        lastMatchId = matchId;
        return now;
    }

    /** Forget everything (seat left, new match). The next poll sends. */
    public void reset() {
        last = null;
        lastMatchId = Integer.MIN_VALUE;
        sentCount = 0;
        sentHead = 0;
        lastSent = Long.MIN_VALUE / 2;
    }

    /**
     * The seat input for a held-key mask (bits: up, down, left, right, A, B, aim up, aim down, aim left, aim right).
     * A also presses START in the lobby and on the results strip (002 §7); the aim pad gives a full-scale stick.
     */
    public static SeatInput toInput(int mask, BusPhase phase) {
        int buttons = 0;
        if ((mask & 1) != 0) {
            buttons |= SeatInput.UP;
        }
        if ((mask & 2) != 0) {
            buttons |= SeatInput.DOWN;
        }
        if ((mask & 4) != 0) {
            buttons |= SeatInput.LEFT;
        }
        if ((mask & 8) != 0) {
            buttons |= SeatInput.RIGHT;
        }
        if ((mask & 16) != 0) {
            buttons |= SeatInput.A;
            if (phase == BusPhase.LOBBY || phase == BusPhase.RESULTS) {
                buttons |= SeatInput.START;
            }
        }
        if ((mask & 32) != 0) {
            buttons |= SeatInput.B;
        }
        int aimY = ((mask >> 7) & 1) * 127 - ((mask >> 6) & 1) * 127;
        int aimX = ((mask >> 9) & 1) * 127 - ((mask >> 8) & 1) * 127;
        return SeatInput.of(buttons, aimX, aimY);
    }

    private boolean underCap(long tick) {
        if (sentCount < MAX_PER_SECOND) {
            return true;
        }
        long oldest = sentAt[sentHead];
        return tick - oldest >= WINDOW_TICKS;
    }

    private void record(long tick) {
        sentAt[sentHead] = tick;
        sentHead = (sentHead + 1) % MAX_PER_SECOND;
        sentCount = Math.min(MAX_PER_SECOND, sentCount + 1);
    }
}
