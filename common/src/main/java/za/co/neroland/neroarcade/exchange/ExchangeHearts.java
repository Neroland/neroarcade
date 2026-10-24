package za.co.neroland.neroarcade.exchange;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Heart deduplication and rate limit (003 §8.1), <b>in memory only</b>. A listing's {@code hearts} is a count; which
 * player hearted what is a preference history, so it is never saved, exported or logged: this set lives for one server
 * session and is cleared on stop. One heart per player per listing per session, at most {@link #PER_HOUR} hearts per
 * player per rolling hour; the author cannot heart their own listing (the caller checks that, since only it knows the
 * author). Server thread only.
 */
public final class ExchangeHearts {

    /** Hearts one player may give per rolling hour. */
    public static final int PER_HOUR = 20;
    /** One hour in milliseconds. */
    public static final long HOUR_MS = 3_600_000L;

    /** Why a heart was refused, or {@link #OK}. */
    public enum Verdict {
        OK,
        ALREADY,
        RATE_LIMITED
    }

    private record HeartKey(UUID player, int listingId) {
    }

    private final Set<HeartKey> given = new HashSet<>();
    private final Map<UUID, Deque<Long>> recent = new HashMap<>();

    /** Records a heart if it is allowed. */
    public Verdict tryHeart(UUID player, int listingId, long nowMillis) {
        HeartKey key = new HeartKey(player, listingId);
        if (given.contains(key)) {
            return Verdict.ALREADY;
        }
        Deque<Long> times = recent.computeIfAbsent(player, p -> new ArrayDeque<>());
        while (!times.isEmpty() && nowMillis - times.peekFirst() >= HOUR_MS) {
            times.pollFirst();
        }
        if (times.size() >= PER_HOUR) {
            return Verdict.RATE_LIMITED;
        }
        times.addLast(nowMillis);
        given.add(key);
        return Verdict.OK;
    }

    /** Whether {@code player} already hearted {@code listingId} this session (for the button state). */
    public boolean hearted(UUID player, int listingId) {
        return given.contains(new HeartKey(player, listingId));
    }

    /** Forgets everything about {@code player} (erasure; the data is transient anyway). */
    public void forget(UUID player) {
        given.removeIf(k -> k.player().equals(player));
        recent.remove(player);
    }

    /** How many listings {@code player} hearted this session (the export reports the count, never which). */
    public int heartsGiven(UUID player) {
        return (int) given.stream().filter(k -> k.player().equals(player)).count();
    }

    /** Erasure-conformance probe: does this session remember anything about {@code player}? */
    public boolean holds(UUID player) {
        return recent.containsKey(player) || given.stream().anyMatch(k -> k.player().equals(player));
    }

    /** Server stop: the set dies with the session. */
    public void clear() {
        given.clear();
        recent.clear();
    }

    /** Entries held (tests). */
    public int size() {
        return given.size();
    }
}
