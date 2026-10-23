package za.co.neroland.neroarcade.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * The leaderboards themselves (003 §6.2), pure so the rules run in a plain JVM: one {@link Board} per lineage root
 * (copies of a cartridge pool onto one board), each a top list of at most {@code maxEntries} rows plus one personal
 * best per player.
 *
 * <p><b>What a row holds:</b> the player's UUID, the score, when (epoch seconds) and — <b>only if the player had
 * opted in to credit when the row was written</b> — their profile name as it was then. Nothing else: no seat, no
 * position, no cartridge author. A personal best is a UUID and a number.
 *
 * <p><b>Rules:</b> an {@code ABANDONED} result writes nothing; a seat that dropped out (or scored 0) updates its
 * personal best only and never gets a top row; a player holds at most one top row per board (their best). Top rows
 * older than the retention are purged; personal bests stay until the player erases their data. The only write is
 * {@link #record}, which is package-private and reached only through {@link LeaderboardStore}.
 */
public final class LeaderboardTable {

    private static final String KEY_BOARDS = "boards";
    private static final String KEY_ROOT = "root";
    private static final String KEY_TOP = "top";
    private static final String KEY_BESTS = "bests";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_SCORE = "score";
    private static final String KEY_AT = "at";
    private static final String KEY_NAME = "name";
    private static final String KEY_TALLIES = "tallies";
    private static final String KEY_WINS = "wins";
    private static final String KEY_RECORDS = "records";

    /** One top-board row. {@code name} is null unless the player had opted in to credit at write time. */
    public record Entry(UUID uuid, int score, long at, @Nullable String name) {
    }

    /** A read-only view of one board. {@code version} changes whenever the board does (marquee resend). */
    public record Board(String lineageRoot, List<Entry> top, Map<UUID, Integer> personalBest, int version) {
        public Board {
            top = List.copyOf(top);
            personalBest = Collections.unmodifiableMap(new LinkedHashMap<>(personalBest));
        }
    }

    private static final class MutableBoard {
        final List<Entry> top = new ArrayList<>();
        final Map<UUID, Integer> bests = new LinkedHashMap<>();
        int version;
    }

    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt(Entry::score).reversed().thenComparingLong(Entry::at);

    private final Map<String, MutableBoard> boards = new LinkedHashMap<>();
    /**
     * Per lineage root: {wins, records} — world counts behind the {@code match_won} / {@code record_set} channels
     * (003 §9). Aggregate numbers only (no player), so they survive purges and erasure.
     */
    private final Map<String, int[]> tallies = new LinkedHashMap<>();
    private int version;

    /**
     * Records a finished match. Package-private: the only caller is {@link LeaderboardStore#record}, itself reached
     * only through the {@code BoardWriter} that {@code MatchHost.finish} holds.
     *
     * @param names the name to store for a player, or null — callers return a name only for opted-in players
     */
    RecordOutcome record(BusMatchResult result, long nowEpoch, int maxEntries, Function<UUID, String> names) {
        if (result == null || result.outcome() == MatchResult.Outcome.ABANDONED || result.seats().isEmpty()) {
            return RecordOutcome.NONE;
        }
        MutableBoard board = boards.computeIfAbsent(result.lineageRoot(), k -> new MutableBoard());
        int topBefore = board.top.isEmpty() ? Integer.MIN_VALUE : board.top.get(0).score();
        int cap = Math.max(1, maxEntries);
        int newBest = 0;
        int entered = 0;
        boolean changed = false;
        for (BusMatchResult.SeatResult seat : result.seats()) {
            if (seat.score() <= 0) {
                continue;
            }
            Integer before = board.bests.get(seat.uuid());
            if (before == null || seat.score() > before) {
                board.bests.put(seat.uuid(), seat.score());
                newBest |= 1 << seat.seat();
                changed = true;
            }
            if (seat.dropped()) {
                continue; // a walk-out never earns a top row (003 §4.4)
            }
            Entry existing = rowOf(board, seat.uuid());
            if (existing != null && existing.score() >= seat.score()) {
                continue;
            }
            if (existing != null) {
                board.top.remove(existing);
            }
            String name = names == null ? null : names.apply(seat.uuid());
            board.top.add(new Entry(seat.uuid(), seat.score(), nowEpoch, name));
            board.top.sort(ORDER);
            while (board.top.size() > cap) {
                board.top.remove(board.top.size() - 1);
            }
            changed = true;
        }
        for (BusMatchResult.SeatResult seat : result.seats()) {
            Entry row = rowOf(board, seat.uuid());
            if (row != null && row.at() == nowEpoch && row.score() == seat.score()) {
                entered |= 1 << seat.seat();
            }
        }
        if (changed) {
            touch(board);
        } else if (board.top.isEmpty() && board.bests.isEmpty()) {
            boards.remove(result.lineageRoot());
        }
        int wins = 0;
        int records = 0;
        if (isWin(result)) {
            int[] t = tallies.computeIfAbsent(result.lineageRoot(), k -> new int[2]);
            t[0] = t[0] == Integer.MAX_VALUE ? t[0] : t[0] + 1;
            wins = t[0];
            version++;
        }
        if (!board.top.isEmpty() && board.top.get(0).at() == nowEpoch && board.top.get(0).score() > topBefore) {
            int[] t = tallies.computeIfAbsent(result.lineageRoot(), k -> new int[2]);
            t[1] = t[1] == Integer.MAX_VALUE ? t[1] : t[1] + 1;
            records = t[1];
            version++;
        }
        return new RecordOutcome(newBest, entered, wins, records);
    }

    /** A win for the {@code match_won} tally: outcome WIN and a human seat that stayed ranked first. */
    static boolean isWin(BusMatchResult result) {
        if (result.outcome() != MatchResult.Outcome.WIN) {
            return false;
        }
        for (BusMatchResult.SeatResult seat : result.seats()) {
            if (seat.rank() == 1 && !seat.dropped()) {
                return true;
            }
        }
        return false;
    }

    /** The board's {wins, records} tally (0, 0 when none). */
    public int[] tally(String lineageRoot) {
        int[] t = tallies.get(lineageRoot);
        return t == null ? new int[2] : t.clone();
    }

    private static @Nullable Entry rowOf(MutableBoard board, UUID uuid) {
        for (Entry e : board.top) {
            if (e.uuid().equals(uuid)) {
                return e;
            }
        }
        return null;
    }

    private void touch(MutableBoard board) {
        board.version++;
        version++;
    }

    /** A board by lineage root. */
    public Optional<Board> board(String lineageRoot) {
        MutableBoard b = boards.get(lineageRoot);
        return b == null ? Optional.empty() : Optional.of(new Board(lineageRoot, b.top, b.bests, b.version));
    }

    public int boardCount() {
        return boards.size();
    }

    /** Changes on every write, purge, strip or erase (cheap "did anything change" for viewers). */
    public int version() {
        return version;
    }

    /**
     * Removes top rows written before {@code nowEpoch - retentionSeconds}; personal bests stay; boards left with
     * no rows and no bests are dropped.
     *
     * @return rows removed
     */
    public int purgeExpired(long nowEpoch, long retentionSeconds) {
        long cutoff = nowEpoch - Math.max(0, retentionSeconds);
        int removed = 0;
        Iterator<Map.Entry<String, MutableBoard>> it = boards.entrySet().iterator();
        while (it.hasNext()) {
            MutableBoard b = it.next().getValue();
            int before = b.top.size();
            b.top.removeIf(e -> e.at() < cutoff);
            int gone = before - b.top.size();
            if (gone > 0) {
                removed += gone;
                touch(b);
            }
            if (b.top.isEmpty() && b.bests.isEmpty()) {
                it.remove();
                version++;
            }
        }
        return removed;
    }

    /** Opt-out: every stored name of {@code player} becomes null. @return true if anything changed */
    public boolean stripNames(UUID player) {
        boolean changed = false;
        for (MutableBoard b : boards.values()) {
            for (int i = 0; i < b.top.size(); i++) {
                Entry e = b.top.get(i);
                if (e.uuid().equals(player) && e.name() != null) {
                    b.top.set(i, new Entry(e.uuid(), e.score(), e.at(), null));
                    touch(b);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Erasure: every top row and personal best of {@code player}, in every board. @return true if anything went */
    public boolean erase(UUID player) {
        boolean changed = false;
        Iterator<Map.Entry<String, MutableBoard>> it = boards.entrySet().iterator();
        while (it.hasNext()) {
            MutableBoard b = it.next().getValue();
            boolean gone = b.top.removeIf(e -> e.uuid().equals(player));
            gone |= b.bests.remove(player) != null;
            if (gone) {
                touch(b);
                changed = true;
            }
            if (b.top.isEmpty() && b.bests.isEmpty()) {
                it.remove();
            }
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    /** Conformance probe: does anything here still mention {@code player}? */
    public boolean holds(UUID player) {
        for (MutableBoard b : boards.values()) {
            if (b.bests.containsKey(player) || rowOf(b, player) != null) {
                return true;
            }
        }
        return false;
    }

    // --- persistence -----------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        boards.forEach((lineage, b) -> {
            CompoundTag board = new CompoundTag();
            board.putString(KEY_ROOT, lineage);
            ListTag top = new ListTag();
            for (Entry e : b.top) {
                CompoundTag row = new CompoundTag();
                row.putString(KEY_UUID, e.uuid().toString());
                row.putInt(KEY_SCORE, e.score());
                row.putLong(KEY_AT, e.at());
                if (e.name() != null) {
                    row.putString(KEY_NAME, e.name());
                }
                top.add(row);
            }
            board.put(KEY_TOP, top);
            ListTag bests = new ListTag();
            b.bests.forEach((uuid, score) -> {
                CompoundTag row = new CompoundTag();
                row.putString(KEY_UUID, uuid.toString());
                row.putInt(KEY_SCORE, score);
                bests.add(row);
            });
            board.put(KEY_BESTS, bests);
            list.add(board);
        });
        root.put(KEY_BOARDS, list);
        ListTag tallyList = new ListTag();
        tallies.forEach((lineage, t) -> {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_ROOT, lineage);
            row.putInt(KEY_WINS, t[0]);
            row.putInt(KEY_RECORDS, t[1]);
            tallyList.add(row);
        });
        root.put(KEY_TALLIES, tallyList);
        return root;
    }

    public static LeaderboardTable load(CompoundTag root) {
        LeaderboardTable table = new LeaderboardTable();
        ListTag tallyList = root.getList(KEY_TALLIES).orElseGet(ListTag::new);
        for (int i = 0; i < tallyList.size(); i++) {
            tallyList.getCompound(i).ifPresent(row -> row.getString(KEY_ROOT).ifPresent(lineage -> table.tallies.put(
                    lineage, new int[] {Math.max(0, row.getIntOr(KEY_WINS, 0)), Math.max(0, row.getIntOr(KEY_RECORDS, 0))})));
        }
        Optional<ListTag> list = root.getList(KEY_BOARDS);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> board = list.get().getCompound(i);
            if (board.isEmpty()) {
                continue;
            }
            try {
                String lineage = board.get().getString(KEY_ROOT).orElseThrow();
                MutableBoard b = new MutableBoard();
                ListTag top = board.get().getList(KEY_TOP).orElseGet(ListTag::new);
                for (int r = 0; r < top.size(); r++) {
                    CompoundTag row = top.getCompound(r).orElseThrow();
                    b.top.add(new Entry(UUID.fromString(row.getString(KEY_UUID).orElseThrow()),
                            row.getInt(KEY_SCORE).orElseThrow(), row.getLong(KEY_AT).orElseThrow(),
                            row.getString(KEY_NAME).orElse(null)));
                }
                b.top.sort(ORDER);
                ListTag bests = board.get().getList(KEY_BESTS).orElseGet(ListTag::new);
                for (int r = 0; r < bests.size(); r++) {
                    CompoundTag row = bests.getCompound(r).orElseThrow();
                    b.bests.put(UUID.fromString(row.getString(KEY_UUID).orElseThrow()),
                            row.getInt(KEY_SCORE).orElseThrow());
                }
                table.boards.put(lineage, b);
            } catch (RuntimeException malformed) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] skipping malformed leaderboard {} ({})", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }
}
