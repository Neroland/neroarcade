package za.co.neroland.neroarcade.privacy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-player export rate limit, in memory only: one {@code /neroarcade privacy export} per
 * {@code privacy.exportCooldownSeconds}. Expired entries are pruned on every use, a failed export refunds its slot,
 * and erasure forgets the player.
 */
public final class ExportCooldown {

    private final Map<UUID, Long> lastAt = new HashMap<>();

    /** 0 when an export may run now (and the slot is taken); otherwise the seconds left. */
    public synchronized long tryAcquire(UUID player, long nowEpochMillis, long cooldownMillis) {
        lastAt.values().removeIf(at -> nowEpochMillis - at >= cooldownMillis || nowEpochMillis < at);
        Long previous = lastAt.get(player);
        if (previous != null) {
            return Math.max(1L, (cooldownMillis - (nowEpochMillis - previous) + 999L) / 1000L);
        }
        lastAt.put(player, nowEpochMillis);
        return 0L;
    }

    /** A failed export gives the slot back. */
    public synchronized void refund(UUID player) {
        lastAt.remove(player);
    }

    /** Erasure. */
    public synchronized void forget(UUID player) {
        lastAt.remove(player);
    }

    /** Conformance probe. */
    public synchronized boolean holds(UUID player) {
        return lastAt.containsKey(player);
    }

    public synchronized void clear() {
        lastAt.clear();
    }

    public synchronized int size() {
        return lastAt.size();
    }
}
