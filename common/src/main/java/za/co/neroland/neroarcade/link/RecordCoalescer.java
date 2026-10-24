package za.co.neroland.neroarcade.link;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Storm control for {@code RECORD_BEATEN} (007 §4, NeroSecurity's {@code PushCoalescer} shape): at most one
 * notification per (player, board) per cooldown, leading edge first. Later falls inside the window are counted and
 * the newest rank kept; when the window closes with a fall pending, {@link #drain} hands back one summary (which
 * opens a new window, so two notifications for one board are always at least a cooldown apart).
 *
 * <p>Pure and in memory only: the caller's clock and cooldown, bounded at {@value #MAX_WINDOWS} windows (the oldest
 * is evicted). Keyed by the recipient and a cartridge id (the board); nothing records who caused a fall.
 */
final class RecordCoalescer {

    static final int MAX_WINDOWS = 4096;

    /** A summary due now: the newest rank of the falls counted inside a closed window. */
    record Due(UUID player, String cartridgeId, @Nullable Integer newRank) {
    }

    private record Key(UUID player, String cartridgeId) {
    }

    private static final class Window {
        final long start;
        boolean pending;
        @Nullable Integer latestRank;

        Window(long start) {
            this.start = start;
        }
    }

    private final Map<Key, Window> windows = new LinkedHashMap<>();

    /**
     * One fall. True = notify now (no open window, or the open one has expired; a new window starts). False = inside
     * the window: the fall is counted and its rank kept for the summary.
     */
    synchronized boolean offer(UUID player, String cartridgeId, @Nullable Integer newRank, long now, long cooldownMs) {
        Key key = new Key(player, cartridgeId);
        Window open = windows.get(key);
        if (open != null && now - open.start < Math.max(0L, cooldownMs)) {
            open.pending = true;
            open.latestRank = newRank;
            return false;
        }
        windows.remove(key);
        while (windows.size() >= MAX_WINDOWS) {
            Iterator<Key> oldest = windows.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        windows.put(key, new Window(now));
        return true;
    }

    /**
     * Closes every expired window. A window with a fall pending yields one {@link Due} and is replaced by a new
     * window starting now; one without is dropped.
     */
    synchronized List<Due> drain(long now, long cooldownMs) {
        List<Due> out = new ArrayList<>();
        List<Key> reopen = new ArrayList<>();
        for (Iterator<Map.Entry<Key, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Window> e = it.next();
            Window w = e.getValue();
            if (now - w.start >= Math.max(0L, cooldownMs)) {
                it.remove();
                if (w.pending) {
                    out.add(new Due(e.getKey().player(), e.getKey().cartridgeId(), w.latestRank));
                    reopen.add(e.getKey());
                }
            }
        }
        for (Key key : reopen) {
            windows.put(key, new Window(now));
        }
        return out;
    }

    synchronized int size() {
        return windows.size();
    }

    synchronized void forget(UUID player) {
        windows.keySet().removeIf(key -> key.player().equals(player));
    }

    synchronized boolean holdsAnyFor(UUID player) {
        for (Key key : windows.keySet()) {
            if (key.player().equals(player)) {
                return true;
            }
        }
        return false;
    }

    synchronized void clear() {
        windows.clear();
    }
}
