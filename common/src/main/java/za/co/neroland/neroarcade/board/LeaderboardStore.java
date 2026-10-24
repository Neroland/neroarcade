package za.co.neroland.neroarcade.board;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.privacy.BackupHook;

/**
 * The world's leaderboards (003 §6.2): a {@link SavedData} on the overworld, read through Core's
 * {@link SavedDataRecovery}, wrapping a {@link LeaderboardTable}. The single write path is the package-private
 * {@link #record}, reached only from the {@code BoardWriter} {@link Boards#writer} builds for a cabinet's
 * {@code MatchHost} ({@code LeaderboardWriteOnlyFromMatchHostTest} scans the sources for any other caller).
 */
public final class LeaderboardStore extends SavedData {

    public static final String RECOVERY_NAME = "neroarcade:leaderboards";

    private static final Codec<LeaderboardStore> CODEC =
            CompoundTag.CODEC.xmap(tag -> new LeaderboardStore(LeaderboardTable.load(tag)), store -> store.table.save());

    private static final Supplier<LeaderboardStore> FACTORY = LeaderboardStore::new;

    public static final SavedDataType<LeaderboardStore> TYPE = new SavedDataType<LeaderboardStore>(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "leaderboards"), FACTORY, CODEC, null);

    /** Where the erasers find this store (tests swap in an instance). */
    public static final za.co.neroland.neroarcade.privacy.StoreAccess<LeaderboardStore> ACCESS =
            new za.co.neroland.neroarcade.privacy.StoreAccess<>(LeaderboardStore::get);

    /** The {@code leaderboards.enabled} kill switch as read by {@link #record} (tests flip it). */
    private static volatile java.util.function.BooleanSupplier recording = NeroArcadeConfig.LEADERBOARDS_ENABLED::get;

    private final LeaderboardTable table;

    /** Test seam for the kill switch; {@code null} restores the config read. */
    static void recordingForTest(java.util.function.BooleanSupplier enabled) {
        recording = enabled == null ? NeroArcadeConfig.LEADERBOARDS_ENABLED::get : enabled;
    }

    public LeaderboardStore() {
        this(new LeaderboardTable());
    }

    /** A store around an existing table (tests and the erasure fixtures). */
    public static LeaderboardStore of(LeaderboardTable table) {
        return new LeaderboardStore(table);
    }

    private LeaderboardStore(LeaderboardTable table) {
        this.table = table;
    }

    public static LeaderboardStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, LeaderboardStore::new, RECOVERY_NAME);
    }

    /**
     * The single write: kill switch, then the table's rules, then one backup per match. Package-private.
     *
     * @param names a player's name to store, or null — only for players opted in to credit
     */
    RecordOutcome record(MinecraftServer server, BusMatchResult result, Function<UUID, String> names) {
        if (!recording.getAsBoolean()) {
            return RecordOutcome.NONE;
        }
        int before = table.version();
        RecordOutcome outcome = table.record(result, System.currentTimeMillis() / 1000L,
                NeroArcadeConfig.LEADERBOARD_MAX_ENTRIES.get(), names);
        if (table.version() != before) {
            setDirty();
            backup(server);
        }
        return outcome;
    }

    /** The board of cartridge {@code cartridgeId} (boards are keyed by cartridge id). */
    public Optional<LeaderboardTable.Board> board(String cartridgeId) {
        return table.board(cartridgeId);
    }

    /** Every board on which {@code player} holds a personal best (read-only views). */
    public java.util.List<LeaderboardTable.Board> boardsWithBest(UUID player) {
        return table.boardsWithBest(player);
    }

    public int version() {
        return table.version();
    }

    /** When {@code player}'s best on {@code cartridgeId} was set (epoch seconds; -1 unknown), if any (export). */
    public Optional<Long> bestSetAt(String cartridgeId, UUID player) {
        return table.bestSetAt(cartridgeId, player);
    }

    /**
     * Drops top rows older than {@code privacy.leaderboardRetentionDays} and personal bests set longer ago than
     * {@code privacy.personalBestRetentionDays}; backs up if anything went.
     */
    public int purgeExpired(MinecraftServer server, long nowEpoch) {
        long retention = NeroArcadeConfig.LEADERBOARD_RETENTION_DAYS.get() * 86_400L;
        long bestRetention = NeroArcadeConfig.PERSONAL_BEST_RETENTION_DAYS.get() * 86_400L;
        int versionBefore = table.version();
        int removed = table.purgeExpired(nowEpoch, retention, bestRetention);
        if (removed > 0) {
            setDirty();
            backup(server);
        } else if (table.version() != versionBefore) {
            setDirty(); // a pre-Stage-6 best had its retention clock started: save it so a restart does not reset it
        }
        return removed;
    }

    /** Credit opt-out: names of {@code player} become null everywhere. */
    public void stripNames(MinecraftServer server, UUID player) {
        if (table.stripNames(player)) {
            setDirty();
            backup(server);
        }
    }

    /** Erasure: every row and personal best of {@code player} (the caller backs up). */
    public boolean erase(UUID player) {
        boolean changed = table.erase(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean holds(UUID player) {
        return table.holds(player);
    }

    /** What this store's saved form (and so its backup) holds. */
    public net.minecraft.nbt.CompoundTag saveForm() {
        return table.save();
    }

    public void backup(MinecraftServer server) {
        BackupHook.current().backup(server, "leaderboards", table::save,
                () -> SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME));
    }
}
