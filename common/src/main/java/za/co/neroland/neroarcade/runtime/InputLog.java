package za.co.neroland.neroarcade.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Append-only log of input <em>changes</em> per seat, keyed by the runtime's step counter
 * ({@link ArcadeRuntime#steps()} before the step, countdown included). Replaying it against the same
 * {@code (game, seed)} reproduces the match exactly.
 *
 * <p><b>Invariants:</b> entries are sorted by {@code (tick, seat)} and unique per key (enforced on
 * {@link #record}); only changes are stored; at most {@link Budgets#MAX_REPLAY_ENTRIES} entries — the next
 * one sets {@link #truncated()} instead of recording, and a truncated log is refused by {@link Replay#play()}.
 * Holds no personal data: a seat is a byte.
 */
public final class InputLog {

    public record Entry(int tick, byte seat, int packed) {
    }

    /** A list of {@code [tick, seat, packed]} int triples. */
    public static final Codec<InputLog> CODEC = Codec.INT.listOf().listOf().comapFlatMap(InputLog::fromTriples,
            InputLog::toTriples);

    private final List<Entry> entries = new ArrayList<>();
    private boolean truncated;

    /** Appends {@code now} for the seat when it differs from {@code previous}. */
    public void record(int tick, int seat, SeatInput now, SeatInput previous) {
        if (now.equals(previous)) {
            return;
        }
        add(tick, seat, now.packed());
    }

    private void add(int tick, int seat, int packed) {
        if (seat < 0 || seat >= Budgets.MAX_SEATS || tick < 0) {
            throw new IllegalArgumentException("bad input log key (" + tick + ", " + seat + ")");
        }
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            if (tick < last.tick() || (tick == last.tick() && seat <= last.seat())) {
                throw new IllegalArgumentException("input log entries must ascend by (tick, seat)");
            }
        }
        if (entries.size() >= Budgets.MAX_REPLAY_ENTRIES) {
            truncated = true;
            return;
        }
        entries.add(new Entry(tick, (byte) seat, new SeatInput(packed).packed()));
    }

    /**
     * Fills {@code carry} with the inputs that change at {@code tick}; seats without a change keep their
     * previous value. Returns {@code carry}.
     */
    public SeatInput[] inputsAt(int tick, SeatInput[] carry) {
        int lo = 0;
        int hi = entries.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (entries.get(mid).tick() < tick) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        for (int i = lo; i < entries.size() && entries.get(i).tick() == tick; i++) {
            Entry entry = entries.get(i);
            if (entry.seat() < carry.length) {
                carry[entry.seat()] = new SeatInput(entry.packed());
            }
        }
        return carry;
    }

    /** Immutable view, sorted by {@code (tick, seat)}. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** True once a record was refused for exceeding {@link Budgets#MAX_REPLAY_ENTRIES}. */
    public boolean truncated() {
        return truncated;
    }

    /** Upper bound of the encoded size: 9 bytes per entry. */
    public int sizeBytes() {
        return entries.size() * 9;
    }

    public InputLog copy() {
        InputLog copy = new InputLog();
        copy.entries.addAll(entries);
        copy.truncated = truncated;
        return copy;
    }

    private static DataResult<InputLog> fromTriples(List<List<Integer>> triples) {
        if (triples.size() > Budgets.MAX_REPLAY_ENTRIES) {
            return DataResult.error(() -> "input log has more than " + Budgets.MAX_REPLAY_ENTRIES + " entries");
        }
        InputLog log = new InputLog();
        for (List<Integer> triple : triples) {
            if (triple.size() != 3) {
                return DataResult.error(() -> "input log entry must be [tick, seat, packed]");
            }
            try {
                log.add(triple.get(0), triple.get(1), triple.get(2));
            } catch (IllegalArgumentException bad) {
                return DataResult.error(bad::getMessage);
            }
        }
        return DataResult.success(log);
    }

    private static List<List<Integer>> toTriples(InputLog log) {
        List<List<Integer>> out = new ArrayList<>(log.entries.size());
        for (Entry entry : log.entries) {
            out.add(List.of(entry.tick(), (int) entry.seat(), entry.packed()));
        }
        return out;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InputLog that && truncated == that.truncated && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
