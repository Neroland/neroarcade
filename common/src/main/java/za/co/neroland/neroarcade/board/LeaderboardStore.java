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

    private final LeaderboardTable table;

    public LeaderboardStore() {
        this(new LeaderboardTable());
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
        if (!NeroArcadeConfig.LEADERBOARDS_ENABLED.get()) {
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

    public Optional<LeaderboardTable.Board> board(String lineageRoot) {
        return table.board(lineageRoot);
    }

    public int version() {
        return table.version();
    }

    /** Drops rows older than {@code privacy.leaderboardRetentionDays}; backs up if anything went. */
    public int purgeExpired(MinecraftServer server, long nowEpoch) {
        long retention = NeroArcadeConfig.LEADERBOARD_RETENTION_DAYS.get() * 86_400L;
        int removed = table.purgeExpired(nowEpoch, retention);
        if (removed > 0) {
            setDirty();
            backup(server);
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

    public void backup(MinecraftServer server) {
        BackupHook.current().backup(server, "leaderboards",
                () -> SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME));
    }
}
