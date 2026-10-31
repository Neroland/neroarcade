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

import za.co.neroland.neroarcade.compat.NbtCompat;
import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * The leaderboards themselves (003 §6.2), pure so the rules run in a plain JVM: one {@link Board} per <b>cartridge
 * id</b> (owner decision D-owner-1, 2026-10-23: duplicates and downloads keep the id and pool onto one board; a remix
 * mints a new id and starts a fresh board, so trivialised rules never post onto the original's), each a top list of
 * at most {@code maxEntries} rows plus one personal best per player.
 *
 * <p><b>Saved form:</b> each board is written under {@code "id"}. A save from before D-owner-1 wrote the lineage root
 * under {@code "root"}; it is read as the board of that id — an original's lineage root <i>is</i> its id, so its
 * board carries over and remixes simply start fresh. Nothing is migrated in place; the next save writes {@code "id"}.
 *
 * <p><b>What a row holds:</b> the player's UUID, the score, when (epoch seconds) and — <b>only if the player had
 * opted in to credit when the row was written</b> — their profile name as it was then. Nothing else: no seat, no
 * position, no cartridge author. A personal best is a UUID, a number and when that number was set (its retention
 * clock: it restarts only when the best improves, so it is not a record of plays).
 *
 * <p><b>Rules:</b> an {@code ABANDONED} result writes nothing; a seat that dropped out (or scored 0) updates its
 * personal best only and never gets a top row; a player holds at most one top row per board (their best). Top rows
 * older than the row retention are purged; personal bests set longer ago than the best retention are purged too
 * (Stage 6 decision on review 003b P3). A best saved before Stage 6 has no time: the first sweep starts its clock
 * instead of deleting it. The only write is
 * {@link #record}, which is package-private and reached only through {@link LeaderboardStore}.
 */
public final class LeaderboardTable {

    private static final String KEY_BOARDS = "boards";
    /** Pre-D-owner-1 saves: the board key was the lineage root. Read only. */
    private static final String KEY_LEGACY_ROOT = "root";
    private static final String KEY_BOARD_ID = "id";
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
    public record Board(String cartridgeId, List<Entry> top, Map<UUID, Integer> personalBest, int version) {
        public Board {
            top = List.copyOf(top);
            personalBest = Collections.unmodifiableMap(new LinkedHashMap<>(personalBest));
        }
    }

    /** A personal best saved before Stage 6: its time is unknown until the next sweep stamps it. */
    static final long UNKNOWN_AT = -1L;

    private static final class MutableBoard {
        final List<Entry> top = new ArrayList<>();
        final Map<UUID, Integer> bests = new LinkedHashMap<>();
        /** When each personal best was set (epoch seconds), same keys as {@link #bests}. */
        final Map<UUID, Long> bestAt = new LinkedHashMap<>();
        int version;
    }

    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt(Entry::score).reversed().thenComparingLong(Entry::at);

    private final Map<String, MutableBoard> boards = new LinkedHashMap<>();
    /**
     * Per cartridge id: {wins, records} — world counts behind the {@code match_won} / {@code record_set} channels
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
        MutableBoard board = boards.computeIfAbsent(result.cartridgeId(), k -> new MutableBoard());
        Map<UUID, Integer> ranksBefore = ranks(board.top, RECORD_WINDOW);
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
                board.bestAt.put(seat.uuid(), nowEpoch);
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
            boards.remove(result.cartridgeId());
        }
        int wins = 0;
        int records = 0;
        if (isWin(result)) {
            int[] t = tallies.computeIfAbsent(result.cartridgeId(), k -> new int[2]);
            t[0] = t[0] == Integer.MAX_VALUE ? t[0] : t[0] + 1;
            wins = t[0];
            version++;
        }
        if (!board.top.isEmpty() && board.top.get(0).at() == nowEpoch && board.top.get(0).score() > topBefore) {
            int[] t = tallies.computeIfAbsent(result.cartridgeId(), k -> new int[2]);
            t[1] = t[1] == Integer.MAX_VALUE ? t[1] : t[1] + 1;
            records = t[1];
            version++;
        }
        return new RecordOutcome(newBest, entered, wins, records,
                displaced(ranksBefore, ranks(board.top, RECORD_WINDOW)));
    }

    /** The top rows a {@code RECORD_BEATEN} event watches (007 §4): the first ten ranks. */
    public static final int RECORD_WINDOW = 10;

    /** Displayed ranks of the first {@code window} rows: 1-based, ties share a rank. Pure. */
    static Map<UUID, Integer> ranks(List<Entry> top, int window) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        int rank = 0;
        int lastScore = Integer.MIN_VALUE;
        for (int i = 0; i < top.size() && i < window; i++) {
            Entry e = top.get(i);
            if (i == 0 || e.score() != lastScore) {
                rank = i + 1;
                lastScore = e.score();
            }
            out.put(e.uuid(), rank);
        }
        return out;
    }

    /** Every player whose rank worsened or who left the window, in their old rank order. Pure. */
    static List<RecordOutcome.Displaced> displaced(Map<UUID, Integer> before, Map<UUID, Integer> after) {
        List<RecordOutcome.Displaced> out = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : before.entrySet()) {
            Integer now = after.get(e.getKey());
            if (now == null || now > e.getValue()) {
                out.add(new RecordOutcome.Displaced(e.getKey(), now));
            }
        }
        return out;
    }

    /** Every board on which {@code player} holds a personal best (the NeroLink {@code library} section). */
    public List<Board> boardsWithBest(UUID player) {
        List<Board> out = new ArrayList<>();
        boards.forEach((id, b) -> {
            if (b.bests.containsKey(player)) {
                out.add(new Board(id, b.top, b.bests, b.version));
            }
        });
        return out;
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
    public int[] tally(String cartridgeId) {
        int[] t = tallies.get(cartridgeId);
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

    /** A board by cartridge id. */
    public Optional<Board> board(String cartridgeId) {
        MutableBoard b = boards.get(cartridgeId);
        return b == null ? Optional.empty() : Optional.of(new Board(cartridgeId, b.top, b.bests, b.version));
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
        return purgeExpired(nowEpoch, retentionSeconds, Long.MAX_VALUE);
    }

    /**
     * Removes top rows written before {@code nowEpoch - retentionSeconds} and personal bests set before
     * {@code nowEpoch - bestRetentionSeconds} (a best with an unknown time is stamped {@code nowEpoch} instead);
     * boards left with no rows and no bests are dropped.
     *
     * @return rows and bests removed
     */
    public int purgeExpired(long nowEpoch, long retentionSeconds, long bestRetentionSeconds) {
        long cutoff = nowEpoch - Math.max(0, retentionSeconds);
        long bestCutoff = bestRetentionSeconds == Long.MAX_VALUE ? Long.MIN_VALUE
                : nowEpoch - Math.max(0, bestRetentionSeconds);
        int removed = 0;
        Iterator<Map.Entry<String, MutableBoard>> it = boards.entrySet().iterator();
        while (it.hasNext()) {
            MutableBoard b = it.next().getValue();
            int before = b.top.size();
            b.top.removeIf(e -> e.at() < cutoff);
            int gone = before - b.top.size();
            for (Map.Entry<UUID, Long> at : b.bestAt.entrySet()) {
                if (at.getValue() == UNKNOWN_AT) {
                    at.setValue(nowEpoch);
                    version++;
                }
            }
            Iterator<Map.Entry<UUID, Long>> bests = b.bestAt.entrySet().iterator();
            while (bests.hasNext()) {
                Map.Entry<UUID, Long> at = bests.next();
                if (at.getValue() < bestCutoff) {
                    b.bests.remove(at.getKey());
                    bests.remove();
                    gone++;
                }
            }
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
            b.bestAt.remove(player);
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

    /** When {@code player}'s personal best on {@code cartridgeId} was set (epoch seconds; -1 unknown), if any. */
    public Optional<Long> bestSetAt(String cartridgeId, UUID player) {
        MutableBoard b = boards.get(cartridgeId);
        return b == null ? Optional.empty() : Optional.ofNullable(b.bestAt.get(player));
    }

    /** Conformance probe: does anything here still mention {@code player}? */
    public boolean holds(UUID player) {
        for (MutableBoard b : boards.values()) {
            if (b.bests.containsKey(player) || b.bestAt.containsKey(player) || rowOf(b, player) != null) {
                return true;
            }
        }
        return false;
    }

    // --- persistence -----------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        boards.forEach((id, b) -> {
            CompoundTag board = new CompoundTag();
            board.putString(KEY_BOARD_ID, id);
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
                long at = b.bestAt.getOrDefault(uuid, UNKNOWN_AT);
                if (at != UNKNOWN_AT) {
                    row.putLong(KEY_AT, at);
                }
                bests.add(row);
            });
            board.put(KEY_BESTS, bests);
            list.add(board);
        });
        root.put(KEY_BOARDS, list);
        ListTag tallyList = new ListTag();
        tallies.forEach((id, t) -> {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_BOARD_ID, id);
            row.putInt(KEY_WINS, t[0]);
            row.putInt(KEY_RECORDS, t[1]);
            tallyList.add(row);
        });
        root.put(KEY_TALLIES, tallyList);
        return root;
    }

    /** The board key of a saved row: {@code "id"}, else a pre-D-owner-1 {@code "root"} (read as that id). */
    private static Optional<String> boardKey(CompoundTag row) {
        Optional<String> id = NbtCompat.getString(row, KEY_BOARD_ID).filter(s -> !s.isEmpty());
        return id.isPresent() ? id : NbtCompat.getString(row, KEY_LEGACY_ROOT).filter(s -> !s.isEmpty());
    }

    public static LeaderboardTable load(CompoundTag root) {
        LeaderboardTable table = new LeaderboardTable();
        ListTag tallyList = NbtCompat.getList(root, KEY_TALLIES).orElseGet(ListTag::new);
        for (int i = 0; i < tallyList.size(); i++) {
            NbtCompat.getCompound(tallyList, i).ifPresent(row -> boardKey(row).ifPresent(id -> table.tallies.put(
                    id, new int[] {Math.max(0, NbtCompat.getIntOr(row, KEY_WINS, 0)), Math.max(0, NbtCompat.getIntOr(row, KEY_RECORDS, 0))})));
        }
        Optional<ListTag> list = NbtCompat.getList(root, KEY_BOARDS);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> board = NbtCompat.getCompound(list.get(), i);
            if (board.isEmpty()) {
                continue;
            }
            try {
                String id = boardKey(board.get()).orElseThrow();
                MutableBoard b = new MutableBoard();
                ListTag top = NbtCompat.getList(board.get(), KEY_TOP).orElseGet(ListTag::new);
                for (int r = 0; r < top.size(); r++) {
                    CompoundTag row = NbtCompat.getCompound(top, r).orElseThrow();
                    b.top.add(new Entry(UUID.fromString(NbtCompat.getString(row, KEY_UUID).orElseThrow()),
                            NbtCompat.getInt(row, KEY_SCORE).orElseThrow(), NbtCompat.getLong(row, KEY_AT).orElseThrow(),
                            NbtCompat.getString(row, KEY_NAME).orElse(null)));
                }
                b.top.sort(ORDER);
                ListTag bests = NbtCompat.getList(board.get(), KEY_BESTS).orElseGet(ListTag::new);
                for (int r = 0; r < bests.size(); r++) {
                    CompoundTag row = NbtCompat.getCompound(bests, r).orElseThrow();
                    UUID who = UUID.fromString(NbtCompat.getString(row, KEY_UUID).orElseThrow());
                    b.bests.put(who, NbtCompat.getInt(row, KEY_SCORE).orElseThrow());
                    b.bestAt.put(who, NbtCompat.getLong(row, KEY_AT).orElse(UNKNOWN_AT));
                }
                table.boards.put(id, b);
            } catch (RuntimeException malformed) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] skipping malformed leaderboard {} ({})", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }
}
