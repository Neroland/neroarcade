package za.co.neroland.neroarcade.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Per (player, action) request limits for the companion app, independent of the bridge's own token bucket (copied
 * from NeroSecurity, 007 §3): {@code export_cartridge} 20 a minute and 200 an hour, {@code import_cartridge} 3 a
 * minute and {@code link.importPerHour} (10) an hour, the {@code leaderboard} section 30 a minute. Sliding windows of
 * accepted call times; refused calls are not counted. In memory only, bounded at {@value #MAX_KEYS} keys (the oldest
 * goes first), cleared for a player by erasure. Pure apart from the caller's clock.
 */
final class LinkRateLimiter {

    /** At most {@code count} calls in any {@code windowMs}. */
    record Rule(int count, long windowMs) {
    }

    static final int MAX_KEYS = 4096;
    static final long MINUTE = 60_000L;
    static final long HOUR = 60L * MINUTE;
    /** The pseudo-action that rate-limits the {@code leaderboard} section. */
    static final String SECTION_LEADERBOARD = "section:leaderboard";

    private record Key(UUID player, String action) {
    }

    private final Function<String, List<Rule>> rules;
    /** Insertion-ordered; when full, the key that has existed longest is evicted first. */
    private final LinkedHashMap<Key, Deque<Long>> calls = new LinkedHashMap<>();

    LinkRateLimiter(Function<String, List<Rule>> rules) {
        this.rules = rules;
    }

    /** The shipped limits; the hourly import cap is read live. */
    static LinkRateLimiter standard(IntSupplier importPerHour) {
        return new LinkRateLimiter(action -> switch (action) {
            case NeroArcadeLinkModule.ACTION_EXPORT -> List.of(new Rule(20, MINUTE), new Rule(200, HOUR));
            case NeroArcadeLinkModule.ACTION_IMPORT -> List.of(new Rule(3, MINUTE),
                    new Rule(Math.max(1, importPerHour.getAsInt()), HOUR));
            case SECTION_LEADERBOARD -> List.of(new Rule(30, MINUTE));
            default -> List.of();
        });
    }

    /**
     * Records the call and returns 0 when every rule allows it; otherwise records nothing and returns how many
     * milliseconds until it would be allowed (at least 1).
     */
    synchronized long tryAcquire(UUID player, String action, long now) {
        List<Rule> forAction = action == null ? null : rules.apply(action);
        if (forAction == null || forAction.isEmpty() || player == null) {
            return 0L;
        }
        long longest = 0L;
        for (Rule rule : forAction) {
            longest = Math.max(longest, rule.windowMs());
        }
        Key key = new Key(player, action);
        Deque<Long> seen = calls.get(key);
        if (seen != null) {
            while (!seen.isEmpty() && now - seen.peekFirst() >= longest) {
                seen.pollFirst();
            }
        }
        long wait = 0L;
        if (seen != null) {
            for (Rule rule : forAction) {
                List<Long> inWindow = new ArrayList<>();
                for (long at : seen) {
                    if (now - at < rule.windowMs()) {
                        inWindow.add(at);
                    }
                }
                if (inWindow.size() >= rule.count()) {
                    // A slot frees when the call that keeps the window full ages out.
                    long freeAt = inWindow.get(inWindow.size() - rule.count()) + rule.windowMs();
                    wait = Math.max(wait, Math.max(1L, freeAt - now));
                }
            }
        }
        if (wait > 0L) {
            return wait;
        }
        if (seen == null) {
            while (calls.size() >= MAX_KEYS) {
                Iterator<Key> oldest = calls.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            seen = new ArrayDeque<>();
            calls.put(key, seen);
        }
        seen.addLast(now);
        return 0L;
    }

    synchronized void forget(UUID player) {
        calls.keySet().removeIf(key -> key.player().equals(player));
    }

    synchronized boolean holdsAnyFor(UUID player) {
        for (Key key : calls.keySet()) {
            if (key.player().equals(player)) {
                return true;
            }
        }
        return false;
    }

    synchronized int size() {
        return calls.size();
    }

    synchronized void clear() {
        calls.clear();
    }
}
