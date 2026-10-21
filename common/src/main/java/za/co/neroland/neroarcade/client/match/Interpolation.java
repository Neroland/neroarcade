package za.co.neroland.neroarcade.client.match;

import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * Fixed-point interpolation between the last two snapshots (002 §8.3): {@code x0 + ((x1 - x0) * partial) >> 8}
 * with {@code partial} 0..256. A jump of more than two cells (a wrap, a respawn) snaps instead of sliding across
 * the board. Cells, scores and the phase never interpolate. Pure.
 */
public final class Interpolation {

    public static final int ONE = 256;
    /** Moves longer than this (fixed point) snap. */
    public static final int SNAP_UNITS = 2 * MatchState.FP;

    private Interpolation() {
    }

    public static int lerp(int from, int to, int partial) {
        int p = Math.max(0, Math.min(ONE, partial));
        if (Math.abs(to - from) > SNAP_UNITS) {
            return p >= ONE / 2 ? to : from;
        }
        return from + (((to - from) * p) >> 8);
    }

    /**
     * How far (0..256) the view is between the previous and the latest snapshot: the time since the latest arrived
     * over the gap between the two arrivals. Renders one snapshot interval behind, so remote entities never
     * extrapolate.
     *
     * @param now             client ticks x 256 (whole ticks shifted left 8, plus the frame's partial tick)
     * @param previousArrival client tick the previous snapshot arrived
     * @param latestArrival   client tick the latest snapshot arrived
     */
    public static int partial(long now, long previousArrival, long latestArrival) {
        long gap = Math.max(1, latestArrival - previousArrival);
        long since = now - (latestArrival << 8);
        if (since <= 0) {
            return 0;
        }
        return (int) Math.min(ONE, since / gap);
    }
}
