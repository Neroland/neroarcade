package za.co.neroland.neroarcade.cabinet;

/**
 * The sleep rule (002 §9, 003 §1 step 4): a host with no seated player <b>and</b> no player within
 * {@code screenRenderRange} for {@code sleepAfterTicks} falls asleep — it drops its runtime, releases its awake
 * slot and shows the attract state. Any seat or nearby player resets the count. Pure.
 */
public final class SleepClock {

    private int idleTicks;

    /**
     * Counts one tick.
     *
     * @return true exactly on the tick the host should fall asleep
     */
    public boolean tick(boolean seated, boolean spectatorNearby, int sleepAfterTicks) {
        if (seated || spectatorNearby) {
            idleTicks = 0;
            return false;
        }
        idleTicks++;
        return idleTicks == Math.max(1, sleepAfterTicks);
    }

    public int idleTicks() {
        return idleTicks;
    }

    public void reset() {
        idleTicks = 0;
    }
}
