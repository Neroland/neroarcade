package za.co.neroland.neroarcade.client.render;

import za.co.neroland.neroarcade.duplicator.DuplicatorRules;

/**
 * The duplicator's client-side copy clock: the block state only says {@code copying}, so the progress bar is timed
 * from the tick the client first saw it true ({@link DuplicatorRules#COPY_TICKS} to full), and a copy that ran
 * (nearly) to the end flashes green for {@value #FLASH_TICKS} ticks when it stops (004 §4.4). A cancelled copy
 * (the original taken out early) does not flash. Pure.
 */
public final class CopyClock {

    public static final int FLASH_TICKS = 10;
    /** A copy seen for at least this many ticks counts as finished when it stops. */
    public static final int FINISHED_TICKS = DuplicatorRules.COPY_TICKS - 10;

    private long startedAt = Long.MIN_VALUE;
    private long flashUntil = Long.MIN_VALUE;

    /** Feeds this frame's state; returns the bar fill 0..1 (0 when idle). */
    public float observe(boolean copying, long now) {
        if (copying) {
            if (startedAt == Long.MIN_VALUE) {
                startedAt = now;
            }
            return Math.min(1f, (now - startedAt) / (float) DuplicatorRules.COPY_TICKS);
        }
        if (startedAt != Long.MIN_VALUE) {
            if (now - startedAt >= FINISHED_TICKS) {
                flashUntil = now + FLASH_TICKS;
            }
            startedAt = Long.MIN_VALUE;
        }
        return 0f;
    }

    /** True while the done flash shows. */
    public boolean flashing(long now) {
        return now < flashUntil;
    }

    /** Nothing to remember any more (safe to forget this clock). */
    public boolean idle(long now) {
        return startedAt == Long.MIN_VALUE && now >= flashUntil;
    }
}
