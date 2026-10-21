package za.co.neroland.neroarcade.cabinet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntSupplier;

import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * The server-wide cap on running cabinets ({@code runtime.maxAwakeCabinets}, 002 §9). A cabinet (or a bench
 * test play) asks for a slot when a seat wakes it; at the cap it is queued FIFO and its screen says
 * {@code neroarcade.cabinet.status.busy} with its place in line. Releasing a slot promotes the head of the
 * queue; the promoted cabinet notices on its next tick through {@link #isAwake}.
 *
 * <p>Keys are opaque (the block entity, or a test object). Server thread only. Runtime only.
 */
public final class AwakeCabinets {

    public enum Grant {
        AWAKE,
        QUEUED
    }

    public static final AwakeCabinets SERVER = new AwakeCabinets(Budgets::maxAwakeCabinets);

    private final IntSupplier cap;
    private final Set<Object> awake = new LinkedHashSet<>();
    private final Deque<Object> queue = new ArrayDeque<>();

    public AwakeCabinets(IntSupplier cap) {
        this.cap = cap;
    }

    /** Takes a slot, or joins the queue (idempotent for a key already awake or queued). */
    public Grant request(Object key) {
        if (awake.contains(key)) {
            return Grant.AWAKE;
        }
        if (queue.contains(key)) {
            return Grant.QUEUED;
        }
        if (awake.size() < Math.max(1, cap.getAsInt())) {
            awake.add(key);
            return Grant.AWAKE;
        }
        queue.addLast(key);
        return Grant.QUEUED;
    }

    /** Frees {@code key}'s slot (or leaves the queue) and promotes queued keys while slots are free. */
    public void release(Object key) {
        boolean freed = awake.remove(key);
        queue.remove(key);
        if (freed) {
            promote();
        }
    }

    public boolean isAwake(Object key) {
        return awake.contains(key);
    }

    /** 1-based place in the queue, or 0 when not queued. */
    public int queuePosition(Object key) {
        int i = 1;
        for (Object queued : queue) {
            if (queued.equals(key)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    public int awakeCount() {
        return awake.size();
    }

    public int queued() {
        return queue.size();
    }

    public void clear() {
        awake.clear();
        queue.clear();
    }

    private void promote() {
        while (!queue.isEmpty() && awake.size() < Math.max(1, cap.getAsInt())) {
            awake.add(queue.removeFirst());
        }
    }
}
